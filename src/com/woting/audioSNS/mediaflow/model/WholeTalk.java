package com.woting.audioSNS.mediaflow.model;

import java.util.HashMap;
import java.util.Map;

import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.push.user.PushUserUDKey;

/**
 * 一次完整的通话：
 * 对讲中是某个人的一次说话
 * 电话中是通话某一方的一次说话
 * @author wanghui
 */
public class WholeTalk {
    private Object lock=new Object();
    protected int maxReSend=0;//最多重传次数
    protected long oneT=80;//一个周期的毫秒数
    private int expiresT=100; //过期周期
    private int reSendContinueExpiresT=3; //持续重转过期周期
    private long lastReceiveTime=System.currentTimeMillis();//最后一次收到数据的时间
    //以上是控制时间的周期

    public WholeTalk() {
        super();
        talkData=new HashMap<Integer, TalkSegment>();
    }

    private IntercomMemory interMem=IntercomMemory.getInstance();
    private String talkId; //通话的Id
    private int talkType; //通话类型，目前仅有两类：1=对讲；2=电话
    private String objId; //通话所对应的对讲的Id，当为对讲时是groupId，当为电话时是callId
    private PushUserUDKey talkerMk; //讲话人Id
    private int MaxNum=0; //当前通话的最大包数
    private int lastNum=0; //最后一个包号
    private Map<Integer, TalkSegment> talkData; //通话的完整数据
    boolean receiveAll=false, sendAll=false;
    public long beginTime=0;
    public long receiveAllTime=0;
    public long sendAllTime=0;

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
    public PushUserUDKey getTalkerMk() {
        return talkerMk;
    }
    public void setTalkerMk(PushUserUDKey talkerMk) {
        this.talkerMk=talkerMk;
    }
    public String getTalkerId() {
        return talkerMk.getUserId();
    }
    public int getLastNum() {
        return this.lastNum;
    }

    /**
     * 加入一段音音频传输
     * @param ts
     */
    public void addSegment(TalkSegment ts) {
        if (ts.getWt()==null||!ts.getWt().getTalkId().equals(talkId)) throw new IllegalArgumentException("对话段的主对话与当前对话段不匹配");
        synchronized(lock) {
            if (lastNum!=0&&Math.abs(ts.getSeqNum())>lastNum) return;
            if (ts.getSeqNum()<0) {
                if (lastNum>0) return;
                lastNum=Math.abs(ts.getSeqNum());
            }
            talkData.put(Math.abs(ts.getSeqNum()), ts);
            if (Math.abs(ts.getSeqNum())>MaxNum) MaxNum=Math.abs(ts.getSeqNum());
            lastReceiveTime=System.currentTimeMillis();
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
                    System.out.println("收到所有的包共用时========================：["+(receiveAllTime-beginTime)+"]毫秒");
                }
            }
        }
    }

    /**
     * 是否传输完成
     * @return
     */
    public boolean isSendCompleted() {
        if (sendAll) return sendAll;
        //计算是否发送完成
        if (lastNum>0&&!sendAll) {
            boolean _sendAll=true;
            for (int i=0; i<lastNum-1; i++) {
                if (talkData.get(i)==null) {
                    _sendAll=false;
                    break;
                }
                boolean isSendOneAll=true;
                for (String k: talkData.get(i).sendUserMap.keySet()) {
                    if (talkData.get(i).sendFlagMap.get(k)<1) {
                        isSendOneAll=false;
                        break;
                    }
                }
                if (!isSendOneAll) {
                    _sendAll=false;
                    break;
                }
            }
            if (_sendAll) {
                sendAllTime=System.currentTimeMillis();
                //System.out.println("发送所有的包共用时=====================：["+(sendAllTime-beginTime)+"]毫秒");
            }
        }

        if (System.currentTimeMillis()-this.lastReceiveTime>oneT*expiresT*10&&lastNum==0) {
            System.out.println("==============由于超时，结束了发送包的过程");
            sendAll=true;
            return sendAll;
        }

        int upperFlag=lastNum>0?(lastNum-1):MaxNum;
        TalkSegment ts=null;

        int j=0;
        for (int i=upperFlag; i>=0||j>expiresT; i--) {//注意，若expiresT个周期内的数据没有收到，则认为还没有全部收到
            ts=talkData.get(i);
            j++;
            if (ts==null||!ts.sendOk()) return false;
        }
        if (lastNum>0) sendAll=true;
        return sendAll;
    }

    /**
     * 结束接收
     */
    public void completedReceive() {
        this.receiveAll=true;
    }
    /**
     * 是否接收到了全部的包
     * @return
     */
    public boolean isReceiveCompleted() {
        if (receiveAll) return receiveAll;

        if (System.currentTimeMillis()-this.lastReceiveTime>oneT*expiresT&&lastNum==0) {
            System.out.println("==============由于超时，结束了接收包的过程");
            receiveAll=true;
            return receiveAll;
        }

        int upperFlag=lastNum>0?lastNum:MaxNum;
        TalkSegment ts;
        int j=0;
        for (int i=upperFlag; i>=0||j>expiresT; i--) {//注意，若expiresT个周期内的数据没有收到，则认为还没有全部收到
            ts=talkData.get(i);
            j++;
            if (ts==null) return false;
        }
        if (lastNum>0) receiveAll=true;

        return receiveAll;
    }

    /**
     * 开始这个通话的监控线程
     */
    public void startMonitor(WholeTalk wt) {
        new Thread("通话[id="+talkId+"]的监控") {
            WholeTalk _wt=wt;
            public void run() {
                _wt.beginTime=System.currentTimeMillis();
                System.out.println("启动通话[id="+talkId+"]监控================================================");
                while (!_wt.isSendCompleted()) {
                    int lowIndex=MaxNum-expiresT;
                    for (int i=(lowIndex>=0?lowIndex:0); i<MaxNum; i++) {//注意，若expiresT个周期内的数据没有收到，则认为还没有全部收到
                        try {
                            TalkSegment ts=talkData.get(i);
                            if (ts!=null) {
                                //1-删除已不在组的用户,只涉及对讲
                                if (wt.getTalkType()==1) {//对讲
                                    String delKeys="";
                                    //找到
                                    OneMeet om=interMem.getOneMeet(_wt.getObjId());
                                    if (om!=null) {
                                        if (om.getEntryGroupUserMap()==null||om.getEntryGroupUserMap().isEmpty()) {
                                            _wt.completedReceive();
                                        } else {
                                            for (String k: ts.getSendUserMap().keySet()) {
                                                if (om.getEntryGroupUserMap().get(k)==null) delKeys+=";"+k;
                                            }
                                        }
                                    } else {
                                        _wt.completedReceive();
                                    }
                                    //删除
                                    if (!delKeys.equals("")) {
                                        String[] _delk=(delKeys.substring(1)).split(";");
                                        for (int j=0; j<_delk.length; j++) {
                                            ts.getSendUserMap().remove(_delk[j]);
                                            ts.getSendFlagMap().remove(_delk[j]);
                                            ts.getSendTimeMap().remove(_delk[j]);
                                        }
                                    }
                                }
                                //2-重新发送
                                for (String k: ts.getSendUserMap().keySet()) {
                                    if (ts.getSendFlagMap().get(k)==1) {//还未传输成功
                                        if (ts.getSendTimeMap().get(k).size()>maxReSend) {
                                            ts.getSendFlagMap().put(k, 3);
                                        } else {
                                            long beginTime=ts.getSendTimeMap().get(k).get(0);
                                            if (System.currentTimeMillis()-beginTime>=oneT*reSendContinueExpiresT) {
                                                ts.getSendFlagMap().put(k, 3);
                                            }
                                        }
                                    }
                                    if (ts.getSendFlagMap().get(k)==1) {//重新发送
//                                        Message bMsg=new Message();
//                                        bMsg.setFromAddr("{(audioflow)@@(www.woting.fm||S)}");
//                                        bMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
//                                        bMsg.setMsgType(1);
//                                        bMsg.setAffirm(1);
//                                        bMsg.setMsgBizType("AUDIOFLOW");
//                                        bMsg.setCmdType(_wt.getTalkType()==1?"TALK_INTERCOM":"TALK_TELPHONE");
//                                        bMsg.setCommand("b1");
//                                        Map<String, Object> dataMap=new HashMap<String, Object>();
//                                        dataMap.put("TalkId", talkId);
//                                        dataMap.put("ObjId", _wt.getObjId());
//                                        dataMap.put("SeqNum", ts.getSeqNum()+"");
//                                        dataMap.put("AudioData", new String(ts.getData()));
//                                        System.out.println("======重发[seqNum="+ts.getSeqNum()+"]="+ts.getSendTimeMap().get(k).size()+"次===============================================");
//                                        bMsg.setMsgContent(dataMap);
//
//                                        String _sp[]=k.split("::");
//                                        MobileKey mk=new MobileKey();
//                                        mk.setMobileId(_sp[0]);
//                                        mk.setPCDType(Integer.parseInt(_sp[1]));
//                                        mk.setUserId(_sp[2]);
//                                        bMsg.setToAddr(MobileUtils.getAddr(mk));
//                                        pmm.getSendMemory().addUniqueMsg2Queue(mk, bMsg, new CompareAudioFlowMsg());
//                                        ts.getSendFlagMap().put(k, 0);
//                                        ts.getSendTimeMap().get(k).add(System.currentTimeMillis());
                                    }
                                }
                            }
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (_wt.isSendCompleted()) {
                    OneMeet om=interMem.getDelData(_wt.getObjId());
                    if (om!=null) {
                        om.releaseSpeaker(_wt.getTalkerMk());
                        Object o=interMem.removeOneMeet(_wt.getTalkerId());
                        if (o!=null) interMem.removeToDelData(_wt.getTalkerId());
                    }
                }
            }
        }.start();
    }
}