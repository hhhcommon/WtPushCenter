package com.woting.audioSNS.intercom.mem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.audioSNS.intercom.monitor.IntercomHandler;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.GroupPo;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.push.user.PushUserUDKey;

public class IntercomMemory {
    private static final ReadWriteLock lock=new ReentrantReadWriteLock(); //读写锁 
    private volatile Object userTalkLck=new Object();

    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static IntercomMemory instance=new IntercomMemory();
    }
    public static IntercomMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    protected ConcurrentHashMap<String, OneMeet> meetMap;//对讲组信息Map
    protected ConcurrentHashMap<String, OneMeet> tobeDelMeetMap;//将要删除的用户组
    protected ConcurrentHashMap<String, Map<String, Object>> userTalk;//用户对讲信息，用户正在用那个通道对讲
    protected ConcurrentHashMap<String, List<OneMeet>> userInMeetMap;//用户对讲信息，用户正在用那个通道对讲

    private IntercomMemory() {
        meetMap=new ConcurrentHashMap<String, OneMeet>();
        tobeDelMeetMap=new ConcurrentHashMap<String, OneMeet>();
        userTalk=new ConcurrentHashMap<String, Map<String, Object>>();
        userInMeetMap=new ConcurrentHashMap<String, List<OneMeet>>();
    }

    /**
     * 把一个新的会议处理控制数据加入内存Map
     * @param oc 新的会议处理控制数据
     * @return 成功返回1，若已经存在这个会话返回0，若这个会话不是新会话返回-1
     */
    public int addOneMeet(OneMeet om) {
        if (om.getStatus()!=0) return -1;//不是新会话
        lock.writeLock().lock();
        try {
            if (meetMap.get(om.getGroupId())!=null) return 0;
            meetMap.put(om.getMeetId(), om);
        } finally {
            lock.writeLock().unlock();
        }
        return 1;
    }

    /**
     * 把一个新的会议处理控制数据加入内存Map
     * @param oc 新的会议处理控制数据
     * @return 成功返回1，若已经存在这个会话返回0，若这个会话不是新会话返回-1
     */
    public int addToBeDelOneMeet(OneMeet om) {
        lock.writeLock().lock();
        try {
            if (tobeDelMeetMap.get(om.getGroupId())!=null) return 0;
            tobeDelMeetMap.put(om.getMeetId(), om);
        } finally {
            lock.writeLock().unlock();
        }
        return 1;
    }
    public OneMeet getDelData(String id) {
        lock.writeLock().lock();
        try {
            return tobeDelMeetMap.get(id);
        } finally {
            lock.writeLock().unlock();
        }
    }
    public void removeToDelData(String id) {
        lock.writeLock().lock();
        try {
            if (tobeDelMeetMap!=null) {
                if (tobeDelMeetMap.get(id)!=null) meetMap.get(id).setStatus_9();
                meetMap.remove(id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 设置正在对讲的用户
     */
    public void setUserTalk(PushUserUDKey pUdk, OneMeet om) {
        synchronized(userTalkLck) {
            Map<String, Object> val=new HashMap<String, Object>();
            val.put("userKey", pUdk);
            val.put("meetData", om);
            userTalk.put(pUdk.getUserId(), val);
        }
    }
    /**
     * 设置正在对讲的用户
     */
    public void removeUserTalk(String userId) {
        synchronized(userTalkLck) {
            userTalk.remove(userId);
        }
    }
    /**
     * 设置正在对讲的用户
     */
    public Map<String, Object> getUserTalk(String userId) {
        synchronized(userTalkLck) {
            return userTalk.get(userId);
        }
    }

    /**
     * 得到组对讲(会议)对象
     * @param gId 组通话Id
     * @return 组对讲对象
     */
    public OneMeet getOneMeet(String gId) {
        lock.readLock().lock();
        try {
            if (meetMap!=null) {
                OneMeet om=meetMap.get(gId);
                if (om!=null) return om;
            }
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    /**
     * 删除组对讲(会议)对象
     * @param gId 组通话Id
     * @return 组对讲对象
     */
    public OneMeet removeOneMeet(String gId) {
        lock.writeLock().lock();
        try {
            if (meetMap!=null) {
                if (meetMap.get(gId)!=null) meetMap.get(gId).setStatus_9();
                return meetMap.remove(gId);
            }
        } finally {
            lock.writeLock().unlock();
        }
        return null;
    }

    /**
     * 得到仍然活动的对讲处理线程
     * @return 返回活动的对讲处理线程列表
     */
    public List<IntercomHandler> getIntercomHanders() {
        List<IntercomHandler> ret=new ArrayList<IntercomHandler>();
        for (String gId: meetMap.keySet()) {
            OneMeet om=meetMap.get(gId);
            if (om!=null&&om.getIntercomHandler()!=null) ret.add(om.getIntercomHandler());
        }
        return ret.isEmpty()?null:ret;
    }

    public List<Map<String, Object>> getActiveGroupList(String userId) {
        if (StringUtils.isNullOrEmptyOrSpace(userId)) return null;
        List<OneMeet> ul=userInMeetMap.get(userId);
        if (ul==null) return null;

        List<Map<String, Object>> rt=new ArrayList<Map<String, Object>>();
        for (OneMeet om: ul) {
            Map<String, Object> m=new HashMap<String, Object>();
            m.put("GroupId", om.getGroupId());
            Group g=om.getGroup();
            if (g==null) continue;
            //组信息
            m.put("GroupInfo", ((GroupPo)g.convert2Po()).toHashMap4View());
            //组内成员信息
            List<Map<String, Object>> gulm=new ArrayList<Map<String, Object>>();
            List<UserPo> upl=g.getUserList();
            if (upl!=null&&!upl.isEmpty()) {
                for (UserPo up: upl) {
                    gulm.add(up.toHashMap4View());
                }
            }
            m.put("GroupUserList", gulm);
            //入组成员Id
            List<String> entryUserIds=new ArrayList<String>();
            if (om.getEntryGroupUserMap()!=null&&!om.getEntryGroupUserMap().isEmpty()) {
                for (String uid: om.getEntryGroupUserMap().keySet()) {
                    entryUserIds.add(uid);
                }
            }
            m.put("GroupEntryUserIds", entryUserIds);
            rt.add(m);
        }
        return rt;
    }
    public void addUserInMeet(String userId, OneMeet om) {
        if (StringUtils.isNullOrEmptyOrSpace(userId)||om==null) return;
        List<OneMeet> ul=userInMeetMap.get(userId);
        if (ul==null) {
            ul=new ArrayList<OneMeet>();
            userInMeetMap.put(userId, ul);
        }

        boolean canIAdd=true;
        for (OneMeet _om: ul) {
            if (_om.equals(om)) {
                canIAdd=false;
                break;
            }
        }
        if (canIAdd) ul.add(om);
    }
    public void removeUserInMeet(String userId, OneMeet om) {
        if (StringUtils.isNullOrEmptyOrSpace(userId)||om==null) return;
        List<OneMeet> ul=userInMeetMap.get(userId);
        if (ul==null||ul.isEmpty()) return;

        for (int i=ul.size()-1; i>=0; i--) {
            if (ul.get(i).equals(om)) {
                ul.remove(i);
                break;
            }
        }
        if (ul.isEmpty()) userInMeetMap.remove(userId);
    }
}