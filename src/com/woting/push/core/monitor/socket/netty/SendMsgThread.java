package com.woting.push.core.monitor.socket.netty;

import com.woting.push.config.MediaConfig;
import com.woting.push.config.PushConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.Channel;

/**
 * 发送我的消息给客户端
 * @author wanghui
 */
public class SendMsgThread extends Thread {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    private PushUserUDKey pUdk;
    private PushConfig pConf;
    private MediaConfig mConf;

    public SendMsgThread(PushUserUDKey pUdk, PushConfig pConf, MediaConfig mConf) {
        this.pUdk=pUdk;
        this.pConf=pConf;
        this.mConf=mConf;
    }
    @Override
    public void run() {
        //找到具体的Channel
        Channel c=(Channel)globalMem.getSocketByPushUser(pUdk);
        if (c==null) return;

        int ctlCount=0, mdaCount=0;
        boolean existMsg=true;
        while (existMsg) {
            ctlCount=0; mdaCount=0;
            Message m=null;
            do {//媒体消息
                try {
                    m=globalMem.sendMem.pollDeviceMsgMDA(pUdk, c);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                if (m!=null) {
                    mdaCount++;
                    try {//传消息
                        if (m instanceof MsgMedia) {
                            MsgMedia mm=(MsgMedia)m;
                            if (System.currentTimeMillis()-mm.getSendTime()<(mm.getMediaType()==1?mConf.get_AudioExpiredTime():mConf.get_VedioExpiredTime())) {
                                c.writeAndFlush(m);
                            }
                        }
                    } catch(Exception e) {}
                }
            } while (m!=null&&ctlCount<10);//先发10条

            do {//控制消息-到设备
                m=null;
                try {
                    m=globalMem.sendMem.pollDeviceMsgCTL(pUdk, c);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                if (m!=null) {
                    ctlCount++;
                    try {//传消息
                        if (m instanceof MsgNormal) {
                            MsgNormal mn=(MsgNormal)m;
                            if (mn.getFromType()==0) {
                                mn.setUserId(pConf.get_ServerType());
                                mn.setDeviceId(pConf.get_ServerName());
                            }
                            c.writeAndFlush(m);
                            //若需要控制确认，插入已发送列表
                            if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, m);
                        }
                    } catch(Exception e) {}
                }
            } while (m!=null);
            
            do {//通知消息（控制消息-到用户）
                m=null;
                try {
                    m=globalMem.sendMem.pollNotifyMsg(pUdk, c);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                if (m!=null&&!(m instanceof MsgMedia)) {
                    ctlCount++;
                    try {//传消息
                        MsgNormal mn=(MsgNormal)m;
                        if (mn.getFromType()==0) {
                            mn.setUserId(pConf.get_ServerType());
                            mn.setDeviceId(pConf.get_ServerName());
                        }
                        c.writeAndFlush(m);
                        //若需要控制确认，插入已发送列表
                        if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, m);
                    } catch(Exception e) {}
                }
            } while (m!=null);

            existMsg=((ctlCount+mdaCount)>0);
        }
        //获得**需要重复发送的消息**
//        LinkedBlockingQueue<Map<String, Object>> mmq=globalMem.sendMem.getResendMsg(pUdk, c);
//        while (mmq!=null&&!mmq.isEmpty()) {
//            Map<String, Object> _m=mmq.poll();
//            if (_m==null||_m.isEmpty()) continue;
//            Message _msg=(Message)_m.get("message");
//            if (_msg==null) continue;
//            c.writeAndFlush(_msg);
//        }
    }
}