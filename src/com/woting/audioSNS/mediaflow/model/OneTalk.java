package com.woting.audioSNS.mediaflow.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.spiritdata.framework.core.cache.SystemCache;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.push.PushConstants;
import com.woting.push.config.MediaConfig;
import com.woting.push.user.PushUserUDKey;

/**
 * 一次完整的通话：
 * 对讲中是某个人的一次说话
 * 电话中是通话某一方的一次说话
 * @author wanghui
 */
public class OneTalk {
    private final Object addLck=new Object();
    private long lastReceiveTime=System.currentTimeMillis();//最后一次收到数据的时间
    private long _expired=-1;
    //以上是控制时间的周期

    public OneTalk(String talkId, PushUserUDKey talkerUdk, String objId, int talkType) {
        super();
        setBeginTime(System.currentTimeMillis());
        _expired=((MediaConfig)SystemCache.getCache(PushConstants.MEDIA_CONF).getContent()).getVedioExpiedTime();
        talkData=new HashMap<Integer, TalkSegment>();
        this.talkId=talkId;
        this.talkerUdk=talkerUdk;
        this.objId=objId;
        this.talkType=talkType;
        //启动监控线程
        moniterTimer=new Timer("通话["+talkId+"::"+talkType+"::"+objId+"::"+talkerUdk.getUserId()+"]监控线程", true);
        moniterTimer.scheduleAtFixedRate(new MonitorOneTalk(), 0, ((MediaConfig)SystemCache.getCache(PushConstants.MEDIA_CONF).getContent()).get_AudioPackT());
    }

    private IntercomMemory interMem=IntercomMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();
    private Timer moniterTimer=new Timer();
    private String talkId; //通话的Id
    private int talkType; //通话类型，目前仅有两类：1=对讲；2=电话
    private String objId; //通话所对应的对讲的Id，当为对讲时是groupId，当为电话时是callId
    private PushUserUDKey talkerUdk; //讲话人Id
    private int lastNum=-1; //最后一个包号
    private Map<Integer, TalkSegment> talkData; //通话的完整数据
    private boolean receiveAll=false, sendAll=false;
    private long beginTime=0;
    private long receiveAllTime=0;
    private long sendAllTime=0;

    public String getTalkId() {
        return talkId;
    }
    public void setTalkId(String talkId) {
        this.talkId=talkId;
    }
    public int getTalkType() {
        return talkType;
    }
    public void setTalkType(int talkType) {
        this.talkType=talkType;
    }
    public String getObjId() {
        return objId;
    }
    public void setObjId(String objId) {
        this.objId=objId;
    }
    public Map<Integer, TalkSegment> getTalkData() {
        return talkData;
    }
    public void setTalkData(Map<Integer, TalkSegment> talkData) {
        this.talkData=talkData;
    }
    public PushUserUDKey getTalkerUdk() {
        return talkerUdk;
    }
    public void setTalkerUdk(PushUserUDKey talkerUdk) {
        this.talkerUdk=talkerUdk;
    }
    public String getTalkerId() {
        return talkerUdk.getUserId();
    }
    public int getLastNum() {
        return this.lastNum;
    }
    public long getBeginTime() {
        return beginTime;
    }
    public void setBeginTime(long beginTime) {
        this.beginTime=beginTime;
    }
    public long getReceiveAllTime() {
        return receiveAllTime;
    }
    public void setReceiveAllTime(long receiveAllTime) {
        this.receiveAllTime=receiveAllTime;
    }
    public long getSendAllTime() {
        return sendAllTime;
    }
    public void setSendAllTime(long sendAllTime) {
        this.sendAllTime=sendAllTime;
    }

    /**
     * 加入一段音音频传输
     * @param ts
     */
    public void addSegment(TalkSegment ts) {
        if (ts.getWt()==null||!ts.getWt().getTalkId().equals(talkId)) throw new IllegalArgumentException("对话段的主对话与当前对话段不匹配");
        lastReceiveTime=System.currentTimeMillis();
        synchronized(addLck) {
            int _num=ts.getSeqNum();
            if (talkData.get(lastNum)!=null) return;
            if (_num<0) {
                if (lastNum+_num==0) return;
                else lastNum=-_num;
            }
            talkData.put(_num, ts);
            //计算是否接收完成
            if (lastNum>0&&!receiveAll) {
                boolean _receiveAll=true;
                for (int i=0; i<lastNum; i++) {
                    if (talkData.get(i)==null) {
                        _receiveAll=false;
                        break;
                    }
                }
                if (_receiveAll) {
                    receiveAllTime=System.currentTimeMillis();
                    receiveAll=true;
                }
            }
            addLck.notifyAll();
        }
    }

    /**
     * 是否传输完成
     * @return
     */
    public boolean isCompleted() {
        if (_expired!=-1&&System.currentTimeMillis()-lastReceiveTime>_expired) {
            sendAll=true;
            receiveAll=true;
            return true;
        }

        //计算是否接收完成
        if (lastNum>0&&!receiveAll) {
            boolean _receiveAll=true;
            for (int i=0; i<lastNum-1; i++) {
                if (talkData.get(i)==null) {
                    _receiveAll=false;
                    break;
                }
            }
            if (_receiveAll&&talkData.get(-lastNum)!=null) {
                receiveAllTime=System.currentTimeMillis();
                receiveAll=true;
            }
        }
        //再计算是否发送完成
        if (receiveAll&&!sendAll) {
            boolean _sendOneAll=true;
            for (int i=0; i<lastNum-1; i++) {
                if (!talkData.get(i).sendOk()) {
                    _sendOneAll=false;
                    break;
                }
            }
            if (_sendOneAll&&talkData.get(-lastNum).sendOk()) {
                sendAllTime=System.currentTimeMillis();
                sendAll=true;
            }
        }
        return (sendAll&&receiveAll);
    }

    private void completeTalk() {
        if (getTalkType()==1) { //对讲
            OneMeet om=interMem.getOneMeet(getObjId());
            if (om!=null) om.releaseSpeaker(getTalkerUdk());
            om=interMem.getDelData(getObjId());
            if (om!=null) {
                Object o=interMem.removeOneMeet(getTalkerId());
                if (o!=null) interMem.removeToDelData(getTalkerId());
            }
        } else
        if (getTalkType()==2) { //电话
            OneCall oc=callingMem.getOneCall(getTalkerId());
            if (oc!=null) oc.releaseSpeaker(getTalkerUdk());
            
        }
        moniterTimer.cancel();
        moniterTimer=null;
    }

    class MonitorOneTalk extends TimerTask {
        @Override
        public void run() {
            if (OneTalk.this.isCompleted()) {
                completeTalk();
            }
        }
    }
}