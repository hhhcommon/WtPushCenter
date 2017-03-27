package com.woting.audioSNS.notify.mem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.woting.audioSNS.notify.model.OneNotifyMsg;
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

    /*
     * 初始化，创建两个主要的对象
     */
    private NotifyMemory() {
        userNotifyMap=new ConcurrentHashMap<String, ArrayList<OneNotifyMsg>>();
    }

    /**
     * 把一个已经发送的通知消息加入内存
     * @param pUdk 发送通知对应的用户设备Key
     * @param notifyMsg 通知消息
     * @return 成功处理返回true
     */
    public boolean putNotifyMsgHadSended(PushUserUDKey pUdk, MsgNormal notifyMsg) {
        String userId=pUdk.getUserId();
        boolean ret=false;
        ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        if (msgList==null) {
            msgList=new ArrayList<OneNotifyMsg>();
            OneNotifyMsg oneNm=new OneNotifyMsg(userId, notifyMsg);
            msgList.add(oneNm);
            userNotifyMap.put(userId, msgList);
            return true;
        } else {
            boolean exist=false;
            for (OneNotifyMsg oneNm: msgList) {
                if (oneNm.equalMsg(notifyMsg)) {
                    ret=oneNm.adjustSendUdk(pUdk);
                    exist=true;
                    break;
                }
            }
            if (!exist) {
                OneNotifyMsg oneNm=new OneNotifyMsg(userId, notifyMsg);
                msgList.add(oneNm);
                return true;
            }
        }
        return ret;
    }

    public List<MsgNormal> getNeedSendNotifyMsg(PushUserUDKey pUdk) {
        String userId=pUdk.getUserId();
        ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(userId);
        if (msgList==null) return null;
        for (int i=msgList.size()-1; i>=0; i--) {
            OneNotifyMsg oneNm=msgList.get(i);
            MsgNormal mn=oneNm.getNeedSendMsg(pUdk);
        }
        return null;
    }

    public void matchCtrAffirmReMsg(MsgNormal m) {
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return;
        ArrayList<OneNotifyMsg> msgList=userNotifyMap.get(pUdk.getUserId());
        if (msgList!=null&&msgList.size()>0) {
            for (OneNotifyMsg oneNm: msgList) {
                if (m.getReMsgId()==null) continue;
                if (m.getReMsgId().equals(oneNm.getNotifyMsg().getMsgId())) {
                    oneNm.setPUDKeyHasRecived(pUdk);
                    break;
                }
            }
        }
    }
}