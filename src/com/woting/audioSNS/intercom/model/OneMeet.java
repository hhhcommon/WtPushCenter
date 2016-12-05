package com.woting.audioSNS.intercom.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.monitor.IntercomHandler;
import com.woting.audioSNS.mediaflow.model.WholeTalk;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.ProcessedMsg;
import com.woting.push.user.PushUserUDKey;

/**
 * 一次会议对讲所需的控制信息<br/>
 * 需要用户组信息。
 * 每个对话组都需要这个对象，若用户不退出组对讲方式，这个对象会一直存在<br/>
 * 此类的实例是一个独享的结构，可以不考虑多线程。<br/>
 * 但消息的写入可能由多个线程处理，因此要采用同步方法addPreMsg。
 * 
 * @author wanghui
 */
public class OneMeet implements Serializable {
    private static final long serialVersionUID=-2635864824531924446L;

    private IntercomMemory interMem=IntercomMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();

    private volatile Object statusLck=new Object();
    private volatile Object preMsgLck=new Object();
    private volatile Object speakerLck=new Object();

    //=1是对讲模式；=2是会议模式；若是0，则表明未设置，采用默认值1
    private int meetType=1;

    //一、组信息
    private Group group;
    public Group getGroup() {
        return group;
    }
    public String getGroupId() {
        if (group==null) return null;
        return group.getGroupId();
    }
    /**
     * 获得会议Id
     * @return 会议Id
     */
    public String getMeetId() {
        if (group!=null) return group.getGroupId();
        return null;
    }


    //二、状态信息
    private volatile int status=0; //通话过程的状态:初始状态
    public int getStatus() {
        synchronized(statusLck) {
            return status;
        }
    }
    public void setStatus_1() {//正常，准备组对讲
        synchronized(statusLck) {
            status=1;
        }
    }
    public void setStatus_2() {//有人申请组通话
        synchronized(statusLck) {
            status=2;
        }
    }
    public void setStatus_3() {//有某人在通话
        synchronized(statusLck) {
            status=3;//这是通话状态
        }
    }
    public void setStatus_4() {//准备关闭
        synchronized(statusLck) {
            status=4;
        }
    }
    public void setStatus_9() {//已经关闭
        synchronized(statusLck) {
            status=9;
        }
    }

    //三、处理线程
    private IntercomHandler interHandler=null;
    public void setIntercomHandler(IntercomHandler interHandler) {
        this.interHandler=interHandler;
    }
    public IntercomHandler getIntercomHandler() {
        return interHandler;
    }

    //四、当前说话的人
    private volatile PushUserUDKey speaker=null;
    /**
     * 设置对讲者
     * @param pUdk 对讲者
     * @return 1可以对讲;2无人在组;3组少于2人;4对讲者不在组;5有人在通话;6自己正在用其他设备通话;7自己正在用点对点通话
     */
    public Map<String, Object> setSpeaker(PushUserUDKey pUdk) {
        synchronized(speakerLck) {
            setStatus_2();
            Map<String, Object> ret=new HashMap<String, Object>();
            int retFlag=0;
            if (meetType==1) { //如果是对讲模式
                if (entryGroupUserMap.size()==0) retFlag=2;//无人在组
                else if (entryGroupUserMap.size()<2) retFlag=3;//少于2人，无需通话
                else if (entryGroupUserMap.get(pUdk.getUserId())==null) retFlag=4;//设置人不在组
                else if (speaker!=null) retFlag=5;//有人在通话
                else if (interMem.getUserTalk(pUdk.getUserId())!=null) retFlag=6;//自己正在用其他设备通话
                else if (callingMem.isTalk(pUdk.getUserId())) retFlag=7;//自己正在用点对点通话
                retFlag=1;
            } else {
                retFlag=1; //如果是会议模式，总是允许说话
            }
            ret.put("retFlag", retFlag);
            if (retFlag==5) ret.put("speakerId", speaker.getUserId());
            if (retFlag==1) {
               setStatus_3();
               speaker=pUdk;
               interMem.setUserTalk(pUdk, this);
            } else setStatus_1();
            return ret;
        }
    }
    /**
     * 释放对讲者，并且关闭所有的传输内容
     * @param pUdk 对讲者
     * @return 
     */
    public int relaseSpeaker(PushUserUDKey pUdk) {
        synchronized(speakerLck) {
            if (speaker==null) return 2;//不存在对讲者
            if (speaker.equals(pUdk)) return 3; //停止者与当前对讲人不一致
            speaker=null;
            interMem.removeUserTalk(pUdk.getUserId());
            setStatus_1();
            return 1;
        }
    }

    //五、消息相关
    private LinkedList<MsgNormal> preMsgQueue=null;//预处理(还未处理)的本对讲(会议)消息
    private List<ProcessedMsg> processedMsgList;//已经处理过的消息
    private List<MsgNormal> sendedMsgList;//已经发出的消息，这里记录仅仅是作为日志的材料
    private Map<PushUserUDKey, List<WholeTalk>> WtsMap=null; //本次对讲所涉及的通话数据
    /**
     * 向预处理队列加入消息
     * @param msg 消息
     */
    public void addPreMsg(MsgNormal msg) {
        synchronized(preMsgLck) {
            this.preMsgQueue.add(msg);
        }
    }
    /**
     * 按照FIFO的队列方式，获取一条待处理的消息，并删除它
     * @return 待处理的消息
     */
    public MsgNormal pollPreMsg() {
        return preMsgQueue.poll();
    }

    public void addProcessedMsg(ProcessedMsg pm) {
        this.processedMsgList.add(pm);
    }
    public List<ProcessedMsg> getProcessedMsgList() {
        return processedMsgList;
    }
    public void addSendedMsg(MsgNormal msg) {
        this.sendedMsgList.add(msg);
    }
    public List<MsgNormal> getSendedMsgList() {
        return sendedMsgList;
    }
    
    //六、进入该组的用户列表
    private Map<String, UserPo> entryGroupUserMap=new HashMap<String, UserPo>();
    public Map<String, UserPo> getEntryGroupUserMap() {
        return entryGroupUserMap;
    }
    /**
     * 进入用户组
     * @param pUdk 用户key
     * @return <pre>
     *   =1,成功
     *   =2,用户已在组 
     *   =4,用户不在该用户组
     *   =5,用户组为空
     *   </pre>
     */
    public int insertEntryUser(PushUserUDKey pUdk) {
        return toggleEntryUser(pUdk, 0);
    }
    /**
     * 退出用户组
     * @param pUdk 用户key
     * @return <pre>
     *   =1,成功
     *   =3,用户不在组 
     *   =4,用户不在该用户组
     *   =5,用户组为空
     *   </pre>
     */
    public int deleteEntryUser(PushUserUDKey pUdk) {
        return toggleEntryUser(pUdk, 1);
    }
    /*
     * 切换用户，用户组的进入与退出
     * @param mk 用户标识
     * @param type 0=进入;1=退出
     * @return 
     *   =1,成功
     *   =2,当为进入：用户已在组
     *   =3,当为推出：用户不在组
     *   =4,用户不在该用户组
     *   =5,用户组为空
     */
    synchronized public int toggleEntryUser(PushUserUDKey pUdk, int type) {
        UserPo entryUp=null;
        List<UserPo> _tl=group.getUserList();
        if (_tl==null||_tl.size()==0) return 5;
        //判断加入的用户是否属于这个组
        boolean exist=false;
        for (UserPo up: _tl) {
            if (pUdk.getUserId().equals(up.getUserId())) {
                exist=true;
                entryUp=up;
                break;
            }
        }
        if (!exist) return 4;

        //用户在加入组Map的状态
        exist=entryGroupUserMap.containsKey(pUdk.getUserId());

        if (type==0) {//进入
            if (exist) return 2; //用户已存在进入组，
            entryGroupUserMap.put(pUdk.getUserId(), entryUp);
        }
        if (type==1) {//退出
            if (exist) entryGroupUserMap.remove(pUdk.getUserId());
            else return 3; //用户不在进入组
        }
        return 1;
    }

    //七1、最后发言时间，用于清除发言人
    private long lastTalkTime=-1;
    public long getLastTalkTime() {
        return lastTalkTime;
    }
    public void setLastTalkTime(String userId) {
        if (speaker!=null&&userId!=null&&userId.equals(speaker.getUserId())) {
            this.lastTalkTime=System.currentTimeMillis();
        }
    }

    //七2、最后被使用时间
    private long lastUsedTime=-1;
    public long getLastUsedTime() {
        return lastUsedTime;
    }
    public void setLastUsedTime() {
        this.lastUsedTime=System.currentTimeMillis();
    }

    /**
     * 一次会议对讲所需的控制信息
     * @param meetType 会议模式 =1是对讲模式；=2是会议模式
     * @param gId 组Id
     */
    public OneMeet(int meetType, Group g) {
        this.meetType=meetType;
        this.group=g;

        status=0;//仅创建，还未处理
        lastTalkTime=System.currentTimeMillis();
        lastUsedTime=lastTalkTime;

        preMsgQueue=new LinkedList<MsgNormal>();
        processedMsgList=new ArrayList<ProcessedMsg>();
        WtsMap=new HashMap<PushUserUDKey, List<WholeTalk>>();
    }

    /**
     * 清除数据
     */
    public void clear() {
        if (WtsMap!=null&&!WtsMap.isEmpty()) {
            for (PushUserUDKey pUdk: WtsMap.keySet()) {
               WtsMap.get(pUdk).clear(); 
            }
        }
    }
}