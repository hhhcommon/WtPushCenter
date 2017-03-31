package com.woting.push.core.monitor.socket.netty.sendthread;

import java.util.List;

import com.woting.audioSNS.mediaflow.MediaConfig;
import com.woting.audioSNS.notify.mem.NotifyMemory;
import com.woting.push.config.PushConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.socket.netty.NettyHandler;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;

/**
 * 发送所有消息给相应客户端
 * @author wanghui
 *
 */
public class SendAllMsg extends Thread {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private NotifyMemory notifyMem=NotifyMemory.getInstance();

    private PushConfig pConf;           //推送配置
    private MediaConfig mConf;          //媒体消息配置

    private PushUserUDKey pUdk;         //用户标识
    private ChannelHandlerContext ctx;  //连接上下文

    public SendAllMsg(PushConfig pConf, MediaConfig mConf, ChannelHandlerContext ctx) {
        this.pConf=pConf;
        this.mConf=mConf;
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {
        if (ctx==null||pUdk==null) return;

        int ctlCount=0, mdaCount=0;
        boolean existMsg=true;
        while (existMsg) {
            ctlCount=0; mdaCount=0;
            Message m=null;
            do {//媒体消息
                try {
                    m=globalMem.sendMem.pollDeviceMsgMDA(pUdk, ctx);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                if (m!=null) {
                    mdaCount++;
                    try {//传消息
                        if (m instanceof MsgMedia) {
                            MsgMedia mm=(MsgMedia)m;
                            if (System.currentTimeMillis()-mm.getSendTime()<(mm.getMediaType()==1?mConf.get_AudioExpiredTime():mConf.get_VedioExpiredTime())) {
                                ctx.writeAndFlush(m);
                            }
                        }
                    } catch(Exception e) {}
                }
            } while (m!=null&&ctlCount<10);//先发10条

            do {//控制消息-到设备
                m=null;
                try {
                    m=globalMem.sendMem.pollDeviceMsgCTL(pUdk, ctx);
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
                            ctx.writeAndFlush(m);
                            //若需要控制确认，插入已发送列表
                            if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, m);
                        }
                    } catch(Exception e) {}
                }
            } while (m!=null);
            existMsg=((ctlCount+mdaCount)>0);
        }
        //获得**需要重复发送的消息**
        List<MsgNormal> mList=globalMem.sendMem.getSendedNeedCtlAffirmMsgANDSend(pUdk, ctx);
        if (mList!=null&&!mList.isEmpty()) {
            for (int i=0; i<mList.size(); i++) ctx.writeAndFlush(mList.get(i));
        }
        //发送通知消息
        List<MsgNormal> notifyMsgList=notifyMem.getNeedSendNotifyMsg(pUdk);
        if (notifyMsgList!=null&&!notifyMsgList.isEmpty()) {
            for (MsgNormal mn: notifyMsgList) {
                ctx.writeAndFlush(mn);
                notifyMem.setNotifyMsgHadSended(pUdk, mn);
                if (mn.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, mn);
            }
        }
    }
}