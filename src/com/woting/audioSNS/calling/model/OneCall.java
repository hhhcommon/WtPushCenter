package com.woting.audioSNS.calling.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.calling.monitor.CallHandler;
import com.woting.audioSNS.mediaflow.model.OneTalk;
import com.woting.push.user.PushUserUDKey;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.ProcessedMsg;

/**
 * 一次通话所需要的数据结构——通话控制数据<br/>
 * 每次通话都由一个线程负责处理，因此这个类的实例是一个独享的结构，可以不考虑多线程。<br/>
 * 但消息的写入可能由多个线程处理，因此要采用同步方法addPreMsg。
 * 
 * @author wanghui
 */
public class OneCall implements Serializable {
    private static final long serialVersionUID=-2635864824531924446L;

    private int callType;//=1是对讲模式；=2是电话模式；若是0，则表明未设置，采用默认值1
    public int getCallType() {
        return callType;
    }

    private volatile PushUserUDKey speaker;
    private volatile Object statuslock=new Object();
    private volatile Object speakerlock=new Object();

    private String callId;//本次通话的Id
    public String getCallId() {
        return callId;
    }

    private PushUserUDKey callerKey;//呼叫者Key
    public PushUserUDKey getCallerKey() {
        return callerKey;
    }
    public void setCallerKey(PushUserUDKey callerKey) {
        this.callerKey=callerKey;
    }
    public String getCallerId() {
        return callerKey==null?null:callerKey.getUserId();
    }
    public int getCallerPcdType() {
        return callerKey==null?null:callerKey.getPCDType();
    }

    private PushUserUDKey callederKey;//被叫者Key
    public PushUserUDKey getCallederKey() {
        return callederKey;
    }
    public void setCallederKey(PushUserUDKey callederKey) {
        this.callederKey=callederKey;
    }
    public String getCallederId() {
        return callederKey==null?null:callederKey.getUserId();
    }
    public int getCallederPcdType() {
        return callederKey==null?null:callederKey.getPCDType();
    }

    private List<PushUserUDKey> callederKeys; //被叫者不同设备的列表
    public List<PushUserUDKey> getCallederList() {
        return callederKeys;
    }
    public void addCallederList(PushUserUDKey callederKey) {
        if (callederKeys==null) {
            callederKeys=new ArrayList<PushUserUDKey>();
        }
        boolean canAdd=true;
        for (int i=0;i<callederKeys.size();i++) {
            if (callederKeys.get(i).equals(callederKey)) {
                canAdd=false;
                break;
            }
        }
        if (canAdd) {
            callederKeys.add(callederKey);
        }
    }

    private long createTime;//本对象创建时间
    public long getCreateTime() {
        return createTime;
    }
    private long beginDialTime;//向“被叫者”发起呼叫的时间
    public long getBeginDialTime() {
        return beginDialTime;
    }
    public void setBeginDialTime() {
        this.beginDialTime=System.currentTimeMillis();
    }
    private long lastUsedTime=-1;//最后被使用时间
    public long getLastUsedTime() {
        return lastUsedTime;
    }
    public void setLastUsedTime() {
        this.lastUsedTime=System.currentTimeMillis();
    }
    private long lastTalkTime=-1;//最后讲话时间，用于定期清除对讲者
    public long getLastTalkTime() {
        return lastTalkTime;
    }
    public void setLastTalkTime(String userId) {
        if (speaker!=null&&userId!=null&&userId.equals(speaker.getUserId())) {
            this.lastTalkTime=System.currentTimeMillis();
        }
    }

    private volatile int status=0; //通话过程的状态:初始状态
    public int getStatus() {
        synchronized(statuslock) {
            return status;
        }
    }
    public void setStatus_1() {//已向“被叫者”发出拨号信息
        synchronized(statuslock) {
            this.status=1;
        }
    }
    public void setStatus_2() {//已收到某一“被叫者”的自动呼叫反馈，等待“被叫者”手工应答
        synchronized(statuslock) {
            this.status=2;
        }
    }
    public void setStatus_3() {//已收到某一“被叫者”的手动反馈反馈ACK，可以通话了
        synchronized(statuslock) {
            this.status=3;//这是通话状态
        }
    }
    public void setStatus_4() {//通话挂断状态
        synchronized(statuslock) {
            this.status=4;
        }
    }
    public void setStatus_9() {//结束对话
        synchronized(statuslock) {
            this.status=9;
        }
    }

    //以下两个对象用来记录App->Server的消息
    private LinkedBlockingQueue<MsgNormal> preMsgQueue;//预处理(还未处理)的本呼叫的消息
    private List<ProcessedMsg> processedMsgList;//已经处理过的消息
    //以下对象用来记录Server->app的消息
    private List<MsgNormal> sendedMsgList;//已经发出的消息，这里记录仅仅是作为日志的材料

    /**
     * 一次通话的结构，这个构造函数限定：
     * 若要构造此类，必须要知呼叫者，被叫者和通话Id。
     * 构造函数还创建了需要内存结构
     * @param callType 通话模式
     * @param callId 通话Id
     * @param callId 通话Id
     * @param callerId 呼叫者Id
     * @param callederId 被叫者Id
     * @param expireOnline 占线判断过期时间
     * @param expireAck 未应答怕判断过期时间
     * @param expireTime 通话过期时间
     */
    public OneCall(int callType, String callId, String callerId, String callederId) {
        super();
        this.speaker=null;
        this.callType=callType;
        if (this.callType==0) this.callType=1;//设置为对讲模式
        this.callId=callId;
        PushUserUDKey callerKey=new PushUserUDKey();
        callerKey.setUserId(callerId);
        PushUserUDKey callederKey=new PushUserUDKey();
        callederKey.setUserId(callederId);
        this.createTime=System.currentTimeMillis();
        this.beginDialTime=-1;//不在使用的情况
        this.lastUsedTime=System.currentTimeMillis();

        this.status=0;//仅创建，还未处理

        this.preMsgQueue=new LinkedBlockingQueue<MsgNormal>();
        this.processedMsgList=new ArrayList<ProcessedMsg>();
        this.sendedMsgList=new ArrayList<MsgNormal>();

        this.callerWts=new ArrayList<OneTalk>();
        this.callederWts=new ArrayList<OneTalk>();
    }

    /**
     * 按照FIFO的队列方式，获取一条待处理的消息，并删除他
     * @return 待处理的消息
     * @throws InterruptedException 
     */
    public MsgNormal takePreMsg() throws InterruptedException {
        return preMsgQueue.take();
    }
    public List<ProcessedMsg> getProcessedMsgList() {
        return processedMsgList;
    }
    public List<MsgNormal> getSendedMsgList() {
        return sendedMsgList;
    }

    //以下为添加对象的方法
    public void putPreMsg(MsgNormal msg) throws InterruptedException {
        preMsgQueue.put(msg);
    }

    public void addSendedMsg(MsgNormal msg) {
        this.sendedMsgList.add(msg);
    }

    public void addProcessedMsg(ProcessedMsg pm) {
        this.processedMsgList.add(pm);
    }

    public PushUserUDKey getOtherUdk(PushUserUDKey oneKey) {
        if (oneKey==null) return null;
        if (oneKey.equals(callederKey)) return this.callerKey;
        if (oneKey.equals(callerKey)) return this.callederKey;
        return null;
    }
    public PushUserUDKey getOtherUdk(String oneId) {
        if (StringUtils.isNullOrEmptyOrSpace(oneId)) return null;
        if (callederKey!=null&&oneId.equals(callederKey.getUserId())) return this.callerKey;
        if (callerKey!=null&&oneId.equals(callerKey.getUserId())) return this.callederKey;
        return null;
    }
    /**
     * 得到另一方信息，返回另一方的KeyList<br/>
     * 可能另一方是多个设备
     * @param oneKey 本方Key
     * @return
     */
    public List<PushUserUDKey> getOthers(PushUserUDKey oneKey) {
        if (oneKey==null) return null;
        if (oneKey.equals(callerKey)) {
            if (getStatus()<3&&callederKey==null) return callederKeys;
            if (getStatus()>=3&&callederKey!=null) {
                List<PushUserUDKey> ret=new ArrayList<PushUserUDKey>();
                ret.add(callederKey);
                return ret;
            }
        }
        if (getStatus()>=3&&oneKey.equals(callederKey)) {
            if (callerKey!=null) {
                List<PushUserUDKey> ret=new ArrayList<PushUserUDKey>();
                ret.add(callerKey);
                return ret;
            }
        } else if (getStatus()<3) {
            List<PushUserUDKey> ret=new ArrayList<PushUserUDKey>();
            boolean find=false;
            if (callederKeys!=null) {
                for (PushUserUDKey _pUdkey: callederKeys) {
                    if (_pUdkey.equals(oneKey)) {
                        find=true;
                    } else ret.add(_pUdkey);
                }
            }
            if (find) {
                if (callerKey!=null) ret.add(callerKey);
                return ret;
            }
        }
        return null;
    }

    /**
     * 设置说话者id
     * @param speakerId 说话者Id
     * @return 返回值：
     *  "1"——设置成功
     *  "0"——由于callType不是对讲模式，不能设置
     *  "-N::描述"——错误的状态：其中N就是状态值，参考status，只有状态3可以通话
     *  "2::当前说话人id"——有人在通话
     */
    public String setSpeaker(PushUserUDKey speaker) {
        if (callType!=1) return "0";
        String ret=null;
        if (this.status!=3) {
            ret="-"+this.status+"::目前状态为["+OneCall.convertStatus(this.status)+"],不能对讲通话";
        } else {
            synchronized (speakerlock) {
                if (this.speaker==null) {
                    this.speaker=speaker;
                    ret="1";
                } else ret="2::"+this.speaker.getUserId();
            }
        }
        return ret;
    }
    /**
     * 获得讲话者
     * @return 讲话者UdKey
     */
    public PushUserUDKey getSpeaker() {
        return speaker;
    }
    /**
     * 清除讲话者
     */
    public void clearSpeaker() {
        speaker=null;
    }

    /**
     * 清除说话者id
     * @param speakerId 说话者Id
     * @return 返回值：
     *  "1"——清除成功
     *  "0"——由于callType不是对讲模式，不能设置
     *  "-N::描述"——错误的状态：其中N就是状态值，参考status，只有状态3可以通话
     *  "2"——清除人和当前说话者不一致
     */
    public String releaseSpeaker(PushUserUDKey speaker) {
        if (callType!=1) return "0";
        String ret=null;
        if (this.status!=3) {
            ret="-"+this.status+"::目前状态为["+OneCall.convertStatus(this.status)+"],不能结束对讲通话";
        } else {
            synchronized (speakerlock) {
                if (this.speaker.equals(speaker)) {
                    this.speaker=null;
                    ret="1";
                } else ret="2::"+this.speaker.getUserId();
            }
        }
        return ret;
    }

    //呼叫者语音信息
    private List<OneTalk> callerWts=null;
    public void addCallerWt(OneTalk callerWt) {
        boolean canAdd=true;
        for (OneTalk wt: this.callerWts) {
            if (wt.getTalkId().equals(callerWt.getTalkId())) {
                canAdd=false;
                break;
            }
        }
        if (canAdd) this.callerWts.add(callerWt);
    }
    public List<OneTalk> getCallerWts() {
        return this.callerWts;
    }
    //被叫者语音信息
    private List<OneTalk> callederWts=null;
    public void addCallederWt(OneTalk callederWt) {
        boolean canAdd=true;
        for (OneTalk wt: this.callederWts) {
            if (wt.getTalkId().equals(callederWt.getTalkId())) {
                canAdd=false;
                break;
            }
        }
        if (canAdd) this.callederWts.add(callederWt);
    }
    public List<OneTalk> getCallederWts() {
        return this.callederWts;
    }

    public void clear() {
        if (callerWts!=null) callerWts.clear();
        if (callederWts!=null) callederWts.clear();
    }

    private static final String convertStatus(int status) {
        if (status==0||status==1||status==2) return "正在呼叫，还未建立连接";
        if (status==3) return "通话中";
        if (status==4) return "正在挂断";
        if (status==9) return "通话已结束";
        
        return "未知状态";
    }

    public PushUserUDKey getUdkByUserId(String speaker) {
        if (speaker==null) return null;
        PushUserUDKey ret=new PushUserUDKey();
        ret.setUserId(speaker);
        if (this.getCallederId().equals(speaker)) {
            if (this.getCallederPcdType()==0) return null;
            ret.setPCDType(this.getCallederPcdType());
        }
        if (this.getCallerId().equals(speaker)) {
            if (this.getCallerPcdType()==0) return null;
            ret.setPCDType(this.getCallerPcdType());
        }
        return ret;
    }

    private CallHandler callHandler=null;
    public void setCallHandler(CallHandler callHandler) {
        this.callHandler=callHandler;
    }
    public CallHandler getCallHandler() {
        return callHandler;
    }
}