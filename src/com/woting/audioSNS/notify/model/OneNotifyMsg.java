package com.woting.audioSNS.notify.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.push.PushConstants;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.user.PushUserUDKey;

public class OneNotifyMsg  implements Serializable {
    private static final long serialVersionUID=-2633864824531924446L;

    private NotifyMessageConfig nmc=null;//通知消息配置

    private MsgNormal notifyMsg=null; //通知消息
    private long firstSendTime=0l;    //首次发送的时间
    private PushUserUDKey bizReUdk=null; //业务回复用户
    private String userId=null; //发送给那个用户
    private Map<PushUserUDKey, SendInfo> sendedMap=null;//已发送到的设备信息

    /**
     * 创建一条通知消息
     * @param toUserId 要发往的用户
     * @param notifyMsg 通知消息
     */
    @SuppressWarnings("unchecked")
    public OneNotifyMsg(String toUserId, MsgNormal notifyMsg) {
        if (StringUtils.isNullOrEmptyOrSpace(toUserId)) throw new IllegalArgumentException("目标用户Id不能为空");
        this.userId=toUserId;
        this.notifyMsg=notifyMsg;
        this.firstSendTime=System.currentTimeMillis();
        this.sendedMap=new HashMap<PushUserUDKey, SendInfo>();
        this.nmc=((CacheEle<NotifyMessageConfig>)SystemCache.getCache(PushConstants.NOTIFY_CONF)).getContent();
    }

    /**
     * 得到需要发送的消息
     * @param pUdk 用户设备Key
     * @return 
     */
    public MsgNormal getNeedSendMsg(PushUserUDKey pUdk) {
        if (!userId.equals(pUdk.getUserId())) return null;
        if (bizReUdk!=null) return null; //若已经有人回复，则本条消息就不用回复了
        if (System.currentTimeMillis()-firstSendTime>nmc.get_ExpireTime()) return null; //超出过去时间，不返回

        SendInfo si=(sendedMap==null?null:sendedMap.get(pUdk));
        if (si==null) return this.notifyMsg; //若没有发送过，则返回这个消息
        if (si.isRecieved()||si.getSendSum()>nmc.get_ExpireLimit()) return null;
        if (System.currentTimeMillis()-si.getFirstSendTime()>si.getSendSum()*nmc.get_Delay()) return this.notifyMsg;
        return null;
    }

    public MsgNormal getNotifyMsg() {
        return this.notifyMsg;
    }
    public void setFirstSendTime(long time) {
        this.firstSendTime=time;
    }

    public boolean equalMsg(MsgNormal notifyMsg) {
        if (notifyMsg==null) return false;
        return notifyMsg.equals(this.notifyMsg);
    }

    public long getFirstSendTime() {
        return firstSendTime;
    }

    public boolean adjustSendUdk(PushUserUDKey pUdk) {
        if (pUdk==null||pUdk.getUserId()==null) return false;
        if (!pUdk.getUserId().equals(this.userId)) return false;
        SendInfo si=sendedMap.get(pUdk);
        if (si==null) sendedMap.put(pUdk, new SendInfo());
        else si.increaseNum();
        return true;
    }

    public void setPUDKeyHasRecived(PushUserUDKey pUdk) {
        SendInfo si=sendedMap.get(pUdk);
        if (si==null) {
            si=new SendInfo();
            sendedMap.put(pUdk, si);
        }
        si.setRecieved();
    }
    public void setBizReUdk(PushUserUDKey pUdk) {
        this.bizReUdk=pUdk;
    }

    public boolean isDealed() {
        return this.bizReUdk!=null;
    }

    class SendInfo {
        int sendNum=1;
        long firstSendTime=0l;
        public SendInfo() {
            firstSendTime=System.currentTimeMillis();
        }
        public void setFirstSendTime(long firstSendTime) {
            this.firstSendTime=firstSendTime;
        }
        public long getFirstSendTime() {
            return firstSendTime;
        }
        public void setSendSum(int sendSum) {
            this.sendNum=sendSum;
        }
        public void increaseNum() {
            if (sendNum!=-1) sendNum++;
        }
        public int getSendSum() {
            return sendNum;
        }
        public void setRecieved() {
            sendNum=-1;
        }
        public boolean isRecieved() {
            return sendNum==-1;
        }
    }

    public Map<PushUserUDKey, SendInfo> getSendedMap() {
        return sendedMap;
    }
    /**
     * 得到需要回复的pUdk的list
     * @param pUdk
     * @return
     */
    public List<PushUserUDKey> getNeedSendAckPUdkList(PushUserUDKey pUdk) {
        List<PushUserUDKey> ret=new ArrayList<PushUserUDKey>();
        for (PushUserUDKey _pUdk: sendedMap.keySet()) {
            if (!_pUdk.equals(pUdk)) ret.add(_pUdk);
        }
        return ret.isEmpty()?null:ret;
    }

    public void setUserId(String userId) {
        this.userId=userId;
    }

    @SuppressWarnings("unchecked")
    public void setSendMapFromJson(String tmpStr) {
        Map<PushUserUDKey, SendInfo> _sendedMap=new HashMap<PushUserUDKey, SendInfo>();
        Map<String, Object> m=(Map<String, Object>)JsonUtils.jsonToObj(tmpStr, Map.class);
        for (String k: m.keySet()) {
            PushUserUDKey pUdk=new PushUserUDKey(k);
            Map<String, Object> _m=(Map<String, Object>)m.get(k);
            SendInfo si=new SendInfo();
            si.setFirstSendTime(Long.parseLong(_m.get("firstSendTime")+""));
            si.setSendSum(Integer.parseInt(_m.get("sendSum")+""));
            _sendedMap.put(pUdk, si);
        }
        if (!_sendedMap.isEmpty()) this.sendedMap=_sendedMap;
    }
}