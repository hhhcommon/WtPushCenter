package com.woting.audioSNS.notify.mem;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.JsonUtils;
import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.audioSNS.notify.model.OneNotifyMsg;
import com.woting.audioSNS.notify.persis.NotifySaveService;
import com.woting.push.PushConstants;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.socket.netty.NettyHandler;
import com.woting.push.core.monitor.socket.netty.event.SendMsgEvent;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * 已至少发送过一次的通知消息
 * @author wanghui
 */
public class NotifyMemory {
    private SessionService sessionService=null;
    private NotifySaveService notifySaveService;

    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static NotifyMemory instance=new NotifyMemory();
    }
    public static NotifyMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    private ConcurrentHashMap<String, List<OneNotifyMsg>> userNotifyMap;
    private NotifyMessageConfig nmc;
    protected BlockingQueue<Map<String, Object>> toDBQueue=null;

    public static void loadNotifyMsgFromDB() {
        NotifyMemory notifyMem=NotifyMemory.getInstance();
        notifyMem._loadNotifyMsgFromDB();
    }
    protected void _loadNotifyMsgFromDB() {
        if (notifySaveService!=null) notifySaveService.fillNotifyFromDB(userNotifyMap);
    }

    /*
     * 初始化，创建两个主要的对象
     */
    @SuppressWarnings("unchecked")
    private NotifyMemory() {
        userNotifyMap=new ConcurrentHashMap<String, List<OneNotifyMsg>>();
        nmc=((CacheEle<NotifyMessageConfig>)SystemCache.getCache(PushConstants.NOTIFY_CONF)).getContent();
        toDBQueue=new LinkedBlockingQueue<Map<String, Object>>();
        notifySaveService=(NotifySaveService)SpringShell.getBean("notifySaveService");
        sessionService=(SessionService)SpringShell.getBean("sessionService");
    }

    /**
     * 把一个已经发送的通知消息加入内存
     * @param pUdk 发送通知对应的用户设备Key
     * @param notifyMsg 通知消息
     * @return 成功处理返回true
     */
    public boolean setNotifyMsgHadSended(PushUserUDKey pUdk, MsgNormal notifyMsg) {
        String userId=pUdk.getUserId();
        Map<String, Object> toDBMap=new HashMap<String, Object>();
        boolean ret=false;
        List<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        OneNotifyMsg oneNm=null;
        if (msgList==null) {
            msgList=new ArrayList<OneNotifyMsg>();
            oneNm=new OneNotifyMsg(userId, notifyMsg);
            msgList.add(oneNm);
            userNotifyMap.put(userId, msgList);
        } else {
            boolean exist=false;
            for (OneNotifyMsg _oneNm: msgList) {
                if (_oneNm.equalMsg(notifyMsg)) {
                    oneNm=_oneNm;
                    exist=true;
                    break;
                }
            }
            if (!exist) {
                oneNm=new OneNotifyMsg(userId, notifyMsg);
                msgList.add(oneNm);
            }
        }
        ret=oneNm.adjustSendUdk(pUdk);
        try {
            toDBMap.put("TYPE", "update");
            toDBMap.put("msgId", notifyMsg.getMsgId());
            toDBMap.put("toUserId", pUdk.getUserId());
            toDBMap.put("sendInfoJson", JsonUtils.objToJson(oneNm.getSendedMap()));
            putSaveDataQueue(toDBMap);
        } catch(Exception e) {
        }
        return ret;
    }

    public List<MsgNormal> getNeedSendNotifyMsg(PushUserUDKey pUdk) {
        String userId=pUdk.getUserId();
        List<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        List<MsgNormal> retList=new ArrayList<MsgNormal>();
        if (msgList==null) return null;
        for (int i=msgList.size()-1; i>=0; i--) {
            OneNotifyMsg oneNm=msgList.get(i);
            MsgNormal mn=oneNm.getNeedSendMsg(pUdk);
            if (mn!=null) retList.add(mn);
        }
        return retList.isEmpty()?null:retList;
    }

    public MsgNormal getNeedSendNotifyMsg(PushUserUDKey pUdk, String msgId) {
        String userId=pUdk.getUserId();
        List<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        List<MsgNormal> retList=new ArrayList<MsgNormal>();
        if (msgList==null) return null;
        for (int i=msgList.size()-1; i>=0; i--) {
            OneNotifyMsg oneNm=msgList.get(i);
            MsgNormal mn=oneNm.getNeedSendMsg(pUdk);
            if (mn!=null&&mn.getMsgId().equals(msgId)) retList.add(mn);
        }
        return null;
    }

    /**
     * 匹配通知消息，让服务器知道用户设备已经收到消息
     * @param m 回复消息
     */
    public void matchCtrAffirmReMsg(MsgNormal m) {
        if (m.getReMsgId()==null) return;
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return;
        List<OneNotifyMsg> msgList=userNotifyMap.get(pUdk.getUserId());
        if (msgList!=null&&msgList.size()>0) {
            for (OneNotifyMsg oneNm: msgList) {
                if (m.getReMsgId().equals(oneNm.getNotifyMsg().getMsgId())) {
                    oneNm.setPUDKeyHasRecived(pUdk);
                    try {
                        Map<String, Object> toDBMap=new HashMap<String, Object>();
                        toDBMap.put("TYPE", "update");
                        toDBMap.put("msgId", m.getReMsgId());
                        toDBMap.put("toUserId", oneNm.getNotifyMsg().getUserId());
                        toDBMap.put("sendInfoJson", JsonUtils.objToJson(oneNm.getSendedMap()));
                        putSaveDataQueue(toDBMap);
                    } catch(Exception e) {
                    }
                    break;
                }
            }
        }
    }

    public void cleanNotifyMsg() {
        List<OneNotifyMsg> msgList=null;
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
     * @param m
     */
    public List<PushUserUDKey> setBizReUdkANDGetNeedSendAckPudkList(MsgNormal m) {
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        OneNotifyMsg oneNm=null;
        if (userNotifyMap!=null&&!userNotifyMap.isEmpty()) {
            List<OneNotifyMsg> msgList=userNotifyMap.get(pUdk.getUserId());
            if (msgList!=null&&!msgList.isEmpty()) {
                for (int i=msgList.size()-1; i>=0; i--) {
                    oneNm=msgList.get(i);
                    if (oneNm.isDealed()) {//已处理过了
                        msgList.remove(i);
                        continue;
                    }
                    if (oneNm.getNotifyMsg().getMsgId().equals(m.getReMsgId())) {
                        oneNm.setBizReUdk(pUdk);
                        try {
                            Map<String, Object> toDBMap=new HashMap<String, Object>();
                            toDBMap.put("TYPE", "update");
                            toDBMap.put("msgId", m.getReMsgId());
                            toDBMap.put("toUserId", m.getUserId());
                            toDBMap.put("bizReUdk", pUdk.toString());
                            toDBMap.put("sendInfoJson", JsonUtils.objToJson(oneNm.getSendedMap()));
                            putSaveDataQueue(toDBMap);
                        } catch(Exception e) {
                        }
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

    /**
     * 从队列中获得需要处理的数据
     * @throws InterruptedException 
     */
    public Map<String, Object> takeSaveDataQueue() throws InterruptedException {
        return this.toDBQueue.take();
    }

    /**
     * 存储要持久化到数据库的数据到队列
     * @param saveData 要持久化到数据库的数据
     * @throws InterruptedException 
     */
    public void putSaveDataQueue(Map<String, Object> saveData) throws InterruptedException {
        saveData.put("lastModifyTime", new Timestamp(System.currentTimeMillis()));
        this.toDBQueue.put(saveData);
    }

    public void putNotifyMsg(String userId, MsgNormal msg) throws InterruptedException {
        if (msg==null||userId==null||userId.trim().length()==0) return;

        Map<String, Object> toDBMap=new HashMap<String, Object>();
        toDBMap.put("TYPE", "insert");
        toDBMap.put("msgId", msg.getMsgId());
        toDBMap.put("toUserId", userId);
        toDBMap.put("msgJson", JsonUtils.objToJson(msg));
        putSaveDataQueue(toDBMap);

        OneNotifyMsg oneNm=null;
        List<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        if (msgList==null) {
            msgList=new ArrayList<OneNotifyMsg>();
            oneNm=new OneNotifyMsg(userId, msg);
            msgList.add(oneNm);
            userNotifyMap.put(userId, msgList);
        } else {
            boolean exist=false;
            for (OneNotifyMsg _oneNm: msgList) {
                if (_oneNm.equalMsg(msg)) {
                    oneNm=_oneNm;
                    exist=true;
                    break;
                }
            }
            if (!exist) {
                oneNm=new OneNotifyMsg(userId, msg);
                msgList.add(oneNm);
            }
        }
        List<PushUserUDKey> usersKey=(List<PushUserUDKey>)sessionService.getActivedUserUDKs(userId);
        if (usersKey!=null&&!usersKey.isEmpty()) {
            for (PushUserUDKey udk: usersKey) {
                ChannelHandlerContext ctx=(ChannelHandlerContext)(PushGlobalMemory.getInstance().getSocketByPushUser(udk));
                Attribute<String> c_cnmId=ctx.channel().attr(NettyHandler.CHANNEL_CURNMID);
                c_cnmId.set(msg.getMsgId());
                if (ctx!=null) ctx.fireUserEventTriggered(SendMsgEvent.NOTIFYMSG_TOBESEND_EVENT);
            }
        }
    }
}