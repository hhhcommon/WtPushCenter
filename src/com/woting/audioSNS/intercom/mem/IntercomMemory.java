package com.woting.audioSNS.intercom.mem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.audioSNS.intercom.monitor.IntercomHandler;
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
    protected ConcurrentHashMap<String, Map<String, Object>> userTalk;//用户对讲信息，用户正在用那个通道对讲

    private IntercomMemory() {
        meetMap=new ConcurrentHashMap<String, OneMeet>();
        userTalk=new ConcurrentHashMap<String, Map<String, Object>>();
    }

    /**
     * 把一个新的会议处理控制数据加入内存Map
     * @param oc 新的会议处理控制数据
     * @return 成功返回1，若已经存在这个会话返回0，若这个会话不是新会话返回-1
     */
    public int addOneMeet(OneMeet om) {
        lock.writeLock().lock();
        try {
            meetMap.put(om.getMeetId(), om);
        } finally {
            lock.writeLock().unlock();
        }
        return 1;
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
    public void removeOneMeet(String gId) {
        lock.writeLock().lock();
        try {
            if (meetMap!=null) {
                if (meetMap.get(gId)!=null) meetMap.get(gId).setStatus_9();
                meetMap.remove(gId);
            }
        } finally {
            lock.writeLock().unlock();
        }
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
}