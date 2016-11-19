package com.woting.audioSNS.intercom.mem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.woting.audioSNS.calling.model.OneCall;
import com.woting.audioSNS.intercom.model.OneMeet;

public class IntercomMemory {
    private static final ReadWriteLock lock=new ReentrantReadWriteLock(); //读写锁 

    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static IntercomMemory instance = new IntercomMemory();
    }
    public static IntercomMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    //对讲组信息Map
    protected ConcurrentHashMap<String, OneMeet> meetMap;
    private IntercomMemory() {
        meetMap=new ConcurrentHashMap<String, OneMeet>();
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
     * 得到组对讲(会议)对象
     * @param gId 组通话Id
     * @return 组对讲对象
     */
    public OneMeet getOneMeet(String gId) {
        return meetMap.get(gId);
    }
}