package com.woting.push.core.monitor.socket.netty.sendthread;

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
 * 发送通知消息给相应客户端
 * @author wanghui
 *
 */
public class SendNotifyMsg extends Thread {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private NotifyMemory notifyMem=NotifyMemory.getInstance();

    private PushConfig pConf;           //推送配置
    private PushUserUDKey pUdk;         //用户标识
    private ChannelHandlerContext ctx;  //连接上下文

    public SendNotifyMsg(PushConfig pConf, ChannelHandlerContext ctx) {
        this.pConf=pConf;
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {
        if (ctx==null||pUdk==null) return;

        Message m=null;
        do {//通知消息（控制消息-到用户）
            m=null;
            try {
                m=globalMem.sendMem.pollNotifyMsg(pUdk, ctx);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            if (m!=null&&!(m instanceof MsgMedia)) {
                try {//传消息
                    MsgNormal mn=(MsgNormal)m;
                    if (mn.getFromType()==0) {
                        mn.setUserId(pConf.get_ServerType());
                        mn.setDeviceId(pConf.get_ServerName());
                    }
                    ctx.writeAndFlush(m);
                    //若需要控制确认，插入已发送列表
                    if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, m);
                    notifyMem.putNotifyMsgHadSended(pUdk, mn);
                } catch(Exception e) {}
            }
        } while (m!=null);
    }
}