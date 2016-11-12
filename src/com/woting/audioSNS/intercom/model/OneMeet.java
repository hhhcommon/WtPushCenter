package com.woting.audioSNS.intercom.model;

import java.io.Serializable;
import java.util.Map;

import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.UserPo;
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
    private int callType;
    //当前说话的人
    private volatile PushUserUDKey speaker;
    //组信息
    private Group group;
    //进入该组的用户列表
    private Map<String, UserPo> entryGroupUserMap;
    //最后发言时间，用于清除发言人
    private long lastTalkTime=-1;

    public Group getGroup() {
        return group;
    }
    public Map<String, UserPo> getEntryGroupUserMap() {
        return entryGroupUserMap;
    }

    public long getLastTalkTime() {
        return lastTalkTime;
    }
    public void setLastTalkTime(String userId) {
        if (speaker!=null&&userId!=null&&userId.equals(speaker.getUserId())) {
            this.lastTalkTime=System.currentTimeMillis();
        }
    }

    /**
     * 得到当前对讲者的Key
     * @return 若无人对讲，返回空
     */
    public PushUserUDKey getSpeaker() {
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
}