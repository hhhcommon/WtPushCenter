package com.woting.audioSNS.calling.mem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.woting.audioSNS.calling.model.OneCall;
import com.woting.audioSNS.calling.monitor.CallHandler;

public class CallingMemory {
    private static final ReadWriteLock lock=new ReentrantReadWriteLock(); //读写锁 

    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static CallingMemory instance=new CallingMemory();
    }
    public static CallingMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    protected ConcurrentHashMap<String, OneCall> callMap;//对讲组信息Map
    protected ConcurrentHashMap<String, Map<String, Object>> userTalk;//用户对讲信息，某个用户正在用那个通道对讲
    protected ConcurrentHashMap<String, OneCall> delMap;//将要被删除的对讲信息，key值是oneCall的id+当前的毫秒数

    private CallingMemory() {
        callMap=new ConcurrentHashMap<String, OneCall>();
        delMap=new ConcurrentHashMap<String, OneCall>();
        userTalk=new ConcurrentHashMap<String, Map<String, Object>>();
    }

    /**
     * 把一个新的会话处理加入内存Map
     * @param oc 新的会话
     * @return 成功返回1；若已经存在这个会话返回0；若这个会话不是新会话返回-1
     */
    public int addOneCall(OneCall oc) {
        if (oc.getStatus()!=0) return -1;//不是新会话
        lock.writeLock().lock();
        try {
            if (callMap.get(oc.getCallId())!=null) return 0;
            callMap.put(oc.getCallId(), oc);
        } finally {
            lock.writeLock().unlock();
        }
        return 1;
    }

    /**
     * 根据callId获得电话通话数据
     * @param callId 电话通话Id
     * @return 电话通话数据
     */
    public OneCall getOneCall(String callId) {
        lock.readLock().lock();
        try {
            if (callMap!=null) {
                OneCall oc=callMap.get(callId);
                if (oc!=null&&oc.getStatus()!=9&&oc.getStatus()!=4) return oc;
            }
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    /**
     * 删除电话通话数据
     * @param callId
     */
    public void removeOneCall(String callId) {
        lock.writeLock().lock();
        try {
            if (callMap!=null) {
                OneCall oc=callMap.get(callId);
                if (oc!=null) oc.setStatus_9();
                callMap.remove(callId);
                delMap.put(callId+"::"+System.currentTimeMillis(), oc);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 得到被删除的通话列表
     * @return 被删除的通话列表
     */
    public Map<String, OneCall> getDelMap() {
        return delMap;
    }

    /**
     * 判断是否有人在通话
     * @param callorId 通话者Id
     * @param callId 通话Id
     * @return 若有人在通话，返回true
     */
    public boolean isTalk(String callederId, String callId) {
        OneCall oc=null;
        lock.readLock().lock();
        try {
            for (String k: callMap.keySet()) {
                oc=callMap.get(k);
                if (oc.getStatus()==9||oc.getCallId().equals(callId)) continue;
                if (oc.getCallerId().equals(callederId)||oc.getCallederId().equals(callederId)) return true;
            }
        } finally {
            lock.readLock().unlock();
        }
        return false;
    }

    /**
     * 得到仍然活动的通话处理线程
     * @return 返回活动的通话处理线程列表
     */
    public List<CallHandler> getCallHanders() {
        List<CallHandler> ret=new ArrayList<CallHandler>();
        for (String callId: callMap.keySet()) {
            OneCall oc=callMap.get(callId);
            if (oc!=null&&oc.getCallHandler()!=null) ret.add(oc.getCallHandler());
        }
        return ret.isEmpty()?null:ret;
    }

    public boolean isTalk(String userId) {
        // TODO Auto-generated method stub
        return false;
    }
}