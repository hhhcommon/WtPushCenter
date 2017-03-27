package com.woting.audioSNS.notify.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.woting.audioSNS.notify.NotifyMessageConfig;
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

    public OneNotifyMsg(String toUserId, MsgNormal notifyMsg) {
        this.userId=toUserId;
        this.notifyMsg=notifyMsg;
        this.firstSendTime=System.currentTimeMillis();
        this.sendedMap=new HashMap<PushUserUDKey, SendInfo>();
    }

    public MsgNormal getNeedSendMsg(PushUserUDKey pUdk) {
        if (bizReUdk==null) return null;
        SendInfo si=sendedMap.get(pUdk);
        if (si==null) return this.notifyMsg;
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

    public boolean adjustSendUdk(PushUserUDKey pUdk) {
        if (pUdk==null||pUdk.getUserId()==null) return false;
        if (!pUdk.getUserId().equals(this.userId)) return false;
        SendInfo si=sendedMap.get(pUdk);
        if (si==null) sendedMap.put(pUdk, new SendInfo());
        else si.increaseNum();
        return true;
    }

    class SendInfo {
        int sendNum=1;
        long firstSendTime=0l;
        public SendInfo() {
            firstSendTime=System.currentTimeMillis();
        }
        public void increaseNum() {
            if (sendNum!=-1) sendNum++;
        }
        public void setRecieved() {
            sendNum=-1;
        }
        public boolean isRecieved() {
            return sendNum==-1;
        }
    }

    public void setPUDKeyHasRecived(PushUserUDKey pUdk) {
        SendInfo si=sendedMap.get(pUdk);
        if (si==null) {
            si=new SendInfo();
            sendedMap.put(pUdk, si);
        }
        si.setRecieved();
        
    }
}