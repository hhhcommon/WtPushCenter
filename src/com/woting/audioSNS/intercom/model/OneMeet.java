package com.woting.audioSNS.intercom.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.woting.audioSNS.intercom.monitor.IntercomHandler;
import com.woting.audioSNS.mediaflow.model.WholeTalk;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.push.core.message.MsgNormal;
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
public class OneMeet  implements Serializable {
    private static final long serialVersionUID=-2635864824531924446L;

    //=1是对讲模式；=2是会议模式；若是0，则表明未设置，采用默认值1
    private int meetType=1;
    //当前说话的人
    private volatile PushUserUDKey speaker=null;
    private volatile Object statuslock=new Object();
    private volatile Object preMsglock=new Object();

    //组信息
    private Group group;
    //进入该组的用户列表
    private Map<PushUserUDKey, UserPo> entryGroupUserMap;

    public Group getGroup() {
        return group;
    }
    public String getGroupId() {
        if (group==null) return null;
        return group.getGroupId();
    }
    public Map<PushUserUDKey, UserPo> getEntryGroupUserMap() {
        return entryGroupUserMap;
    }

    //最后发言时间，用于清除发言人
    private long lastTalkTime=-1;
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
    public void setStatus_1() {//正常，准备组对讲
        synchronized(statuslock) {
            this.status=1;
        }
    }
    public void setStatus_2() {//有人申请组通话
        synchronized(statuslock) {
            this.status=2;
        }
    }
    public void setStatus_3() {//有某人在通话
        synchronized(statuslock) {
            this.status=3;//这是通话状态
        }
    }
    public void setStatus_4() {//准备关闭
        synchronized(statuslock) {
            this.status=4;
        }
    }
    public void setStatus_9() {//已经关闭
        synchronized(statuslock) {
            this.status=9;
        }
    }

    private LinkedList<MsgNormal> preMsgQueue=null;//预处理(还未处理)的本对讲(会议)消息
    private Map<PushUserUDKey, List<WholeTalk>> WtsMap=null; //本次对讲所涉及的通话数据
    
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

        preMsgQueue=new LinkedList<MsgNormal>();
        WtsMap=new HashMap<PushUserUDKey, List<WholeTalk>>();
    }
    /**
     * 得到当前对讲者的Key
     * @return 若无人对讲，返回空
     */
    public PushUserUDKey getSpeaker() {
        if (meetType==1) { //如果是对讲模式
            
        }
        return speaker;
    }

    /**
     * 获得会议Id
     * @return 会议Id
     */
    public String getMeetId() {
        if (group!=null) return group.getGroupId();
        return null;
    }

    /**
     * 向预处理队列加入消息
     * @param msg 消息
     */
    public void addPreMsg(MsgNormal msg) {
        synchronized(preMsglock) {
            this.preMsgQueue.add(msg);
        }
    }

    private IntercomHandler interHandler=null;
    public void setIntercomHandler(IntercomHandler interHandler) {
        this.interHandler=interHandler;
    }
    public IntercomHandler getIntercomHandler() {
        return interHandler;
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