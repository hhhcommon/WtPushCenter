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
    /**
     * 用户在那个对讲组内：记录了那个用户用什么设备在那个用户组内
     * userInMeets-Map：key为用户id，value为下面描述的Map
     *             Map：key为设备类型，value为设备oneMeet对象的List
     */
    protected ConcurrentHashMap<String, Map<String, Object>> userInMeets;

    private IntercomMemory() {
        meetMap=new ConcurrentHashMap<String, OneMeet>();
        userTalk=new ConcurrentHashMap<String, Map<String, Object>>();
        userInMeets=new ConcurrentHashMap<String, Map<String, Object>>();
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
     * 进入组时加入用户会议数据对照表
     * @param pUdk 用户信息
     * @param om 会议数据
     */
    @SuppressWarnings("unchecked")
    public void addUserInMeets(PushUserUDKey pUdk, OneMeet om) {
        if (pUdk==null||om==null) return;
        String userId=pUdk.getUserId();
        if (StringUtils.isNullOrEmptyOrSpace(userId)) return;

        Map<String, Object> userMeets=userInMeets.get(userId);
        if (userMeets==null) {
            userMeets=new HashMap<String, Object>();
            userInMeets.put(userId, userMeets);
        }
        List<OneMeet> oml=(List<OneMeet>)userMeets.get(pUdk.getPCDType()+"");
        if (oml==null) {
            oml=new ArrayList<OneMeet>();
            userMeets.put(pUdk.getPCDType()+"", oml);
        }
        boolean find=false;
        if (!oml.isEmpty()) {
            for (OneMeet _om:oml) {
                if (_om.getGroupId().equals(om.getGroupId())) {
                    find=true;
                    break;
                }
            }
        }
        if (!find) oml.add(om);
    }

    /**
     * 退出组时删除用户会议数据对照表
     * @param pUdk 用户信息
     * @param om 会议数据
     * @return 删除对象个数:-1是处理有异常；0未删除对象
     */
    @SuppressWarnings("unchecked")
    public int removeUserInMeets(PushUserUDKey pUdk, OneMeet om) {
        int ret=0;
        if (pUdk==null||om==null) return -1;
        String userId=pUdk.getUserId();
        if (StringUtils.isNullOrEmptyOrSpace(userId)) return -1;
        Map<String, Object> userMeets=userInMeets.get(userId);
        if (userMeets==null) return 0;
        List<OneMeet> oml=(List<OneMeet>)userMeets.get(pUdk.getPCDType()+"");
        if (oml==null||oml.isEmpty()) return 0;
        if (!oml.isEmpty()) {
            for (int i=oml.size()-1; i>=0; i--) {
                OneMeet _om=oml.get(i);
                if (_om.getGroupId().equals(om.getGroupId())) {
                    oml.remove(i);
                    ret++;
                    break;
                }
            }
            if (oml.isEmpty()) userMeets.remove(pUdk.getPCDType()+"");
        }
        if (userMeets.isEmpty()) userInMeets.remove(userId);
        return ret;
    }
    /**
     * 删除用户会议数据对照表，把该用户设备下的所有对象都删除掉
     * @param pUdk 用户信息
     * @return 删除对象个数:-1是处理有异常；0未删除对象
     */
    @SuppressWarnings("unchecked")
    public int removeUserInMeets(PushUserUDKey pUdk) {
        int ret=0;
        if (pUdk==null) return -1;
        String userId=pUdk.getUserId();
        if (StringUtils.isNullOrEmptyOrSpace(userId)) return -1;
        Map<String, Object> userMeets=userInMeets.get(userId);
        if (userMeets==null) return 0;
        List<OneMeet> oml=(List<OneMeet>)userMeets.get(pUdk.getPCDType()+"");
        if (oml==null) ret=0;
        else {
            ret=oml.size();
            oml.clear();
            userMeets.remove(pUdk.getPCDType()+"");
        }
        if (userMeets.isEmpty()) userInMeets.remove(userId);

        return ret;
    }
    /**
     * 删除用户会议数据对照表，把该用户下的所有对象都删除掉
     * @param String userId 用户信息
     * @return 删除对象个数:-1是处理有异常；0未删除对象
     */
    @SuppressWarnings("unchecked")
    public int removeUserInMeets(String userId) {
        int ret=0;
        if (StringUtils.isNullOrEmptyOrSpace(userId)) return -1;
        Map<String, Object> userMeets=userInMeets.get(userId);
        if (userMeets==null) return 0;
        for (String pcdType: userMeets.keySet()) {
            List<OneMeet> oml=(List<OneMeet>)userMeets.get(pcdType);
            if (oml==null) ret+=0;
            else {
                ret+=oml.size();
                oml.clear();
                userMeets.remove(pcdType);
            }
        }
        userInMeets.remove(userId);
        return ret;
    }

    public Map<String, Object> getUserInMeets(String userId) {
        if (StringUtils.isNullOrEmptyOrSpace(userId)) return null;
        return userInMeets.get(userId);
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