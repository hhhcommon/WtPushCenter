package com.woting.audioSNS.notify.mem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.audioSNS.notify.model.OneNotifyMsg;
import com.woting.push.PushConstants;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.user.PushUserUDKey;

/**
 * 已至少发送过一次的通知消息
 * @author wanghui
 */
public class NotifyMemory {
    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static NotifyMemory instance=new NotifyMemory();
    }
    public static NotifyMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end


    private ConcurrentHashMap<String, ArrayList<OneNotifyMsg>> userNotifyMap;
    private NotifyMessageConfig nmc;

    /*
     * 初始化，创建两个主要的对象
     */
    @SuppressWarnings("unchecked")
    private NotifyMemory() {
        userNotifyMap=new ConcurrentHashMap<String, ArrayList<OneNotifyMsg>>();
        nmc=((CacheEle<NotifyMessageConfig>)SystemCache.getCache(PushConstants.NOTIFY_CONF)).getContent();
    }

    /**
     * 把一个已经发送的通知消息加入内存
     * @param pUdk 发送通知对应的用户设备Key
     * @param notifyMsg 通知消息
     * @return 成功处理返回true
     */
    public boolean setNotifyMsgHadSended(PushUserUDKey pUdk, MsgNormal notifyMsg) {
        String userId=pUdk.getUserId();
        boolean ret=false;
        ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        OneNotifyMsg oneNm=null;
        if (msgList==null) {
            msgList=new ArrayList<OneNotifyMsg>();
            oneNm=new OneNotifyMsg(userId, notifyMsg);
            oneNm.setFirstSendTime(System.currentTimeMillis());
            msgList.add(oneNm);
            userNotifyMap.put(userId, msgList);
            return true;
        } else {
            boolean exist=false;
            for (OneNotifyMsg _oneNm: msgList) {
                if (_oneNm.equalMsg(notifyMsg)) {
                    ret=_oneNm.adjustSendUdk(pUdk);
                    exist=true;
                    break;
                }
            }
            if (!exist) {
                oneNm=new OneNotifyMsg(userId, notifyMsg);
                oneNm.setFirstSendTime(System.currentTimeMillis());
                msgList.add(oneNm);
                return true;
            }
        }
        return ret;
    }

    public List<MsgNormal> getNeedSendNotifyMsg(PushUserUDKey pUdk) {
        String userId=pUdk.getUserId();
        ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        List<MsgNormal> retList=new ArrayList<MsgNormal>();
        if (msgList==null) return null;
        for (int i=msgList.size()-1; i>=0; i--) {
            OneNotifyMsg oneNm=msgList.get(i);
            MsgNormal mn=oneNm.getNeedSendMsg(pUdk);
            if (mn!=null) retList.add(mn);
        }
        return retList.isEmpty()?null:retList;
    }

    /**
     * 匹配通知消息，让服务器知道用户设备已经收到消息
     * @param m 回复消息
     */
    public void matchCtrAffirmReMsg(MsgNormal m) {
        if (m.getReMsgId()==null) return;
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return;
        ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(pUdk.getUserId());
        if (msgList!=null&&msgList.size()>0) {
            for (OneNotifyMsg oneNm: msgList) {
                if (m.getReMsgId().equals(oneNm.getNotifyMsg().getMsgId())) {
                    oneNm.setPUDKeyHasRecived(pUdk);
                    break;
                }
            }
        }
    }

    public void cleanNotifyMsg() {
        ArrayList<OneNotifyMsg> msgList=null;
        List<String> tobeDelUserIdList=new ArrayList<String>();
        OneNotifyMsg oneNm=null;
        if (userNotifyMap!=null&&!userNotifyMap.isEmpty()) {
            for (String userId: userNotifyMap.keySet()) {
                msgList=userNotifyMap.get(userId);
                if (msgList==null||msgList.isEmpty()) {
                    tobeDelUserIdList.add(userId);
                    continue;
                } else {
                    for (int i=msgList.size()-1; i>=0; i--) {
                        oneNm=msgList.get(i);
                        if (oneNm.isDealed()||(System.currentTimeMillis()-oneNm.getFirstSendTime()>nmc.get_ExpireTime())) msgList.remove(i);
                    }
                    if (msgList.isEmpty()) tobeDelUserIdList.add(userId);
               }
            }
            for (String userId: tobeDelUserIdList) userNotifyMap.remove(userId);
        }
    }

    /**
     * 根据回执消息，处理业务回复
     * @param sourceMsg
     */
    public List<PushUserUDKey> setBizReUdkANDGetNeedSendAckPudkList(MsgNormal m) {
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        OneNotifyMsg oneNm=null;
        if (userNotifyMap!=null&&!userNotifyMap.isEmpty()) {
            ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(pUdk.getUserId());
            if (msgList!=null&&!msgList.isEmpty()) {
                for (int i=msgList.size()-1; i>=0; i--) {
                    oneNm=msgList.get(i);
                    if (oneNm.isDealed()) {//已处理过了
                        msgList.remove(i);
                        continue;
                    }
                    if (oneNm.getNotifyMsg().getMsgId().equals(m.getReMsgId())) {
                        oneNm.setBizReUdk(pUdk);
                        //TODO
                        //按业务回复处理必要的业务：目前没有，先不做
                        msgList.remove(i);
                        return oneNm.getNeedSendAckPUdkList(pUdk);
                    }
                }
            }
        }
        return null;
    }
}