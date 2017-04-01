package com.woting.push.core.monitor.socket.netty.sendthread;

import com.woting.audioSNS.notify.mem.NotifyMemory;
import com.woting.push.config.PushConfig;
import com.woting.push.core.mem.PushGlobalMemory;
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
    private String msgId;               //要发送的消息的Id
    private ChannelHandlerContext ctx;  //连接上下文

    public SendNotifyMsg(PushConfig pConf, ChannelHandlerContext ctx) {
        this.pConf=pConf;
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
            this.msgId=ctx.channel().attr(NettyHandler.CHANNEL_CURNMID).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {
        if (ctx==null||pUdk==null) return;
        MsgNormal nmn=notifyMem.getNeedSendNotifyMsg(pUdk, msgId);
        if (nmn!=null) {
            if (nmn.getFromType()==0) {
                nmn.setUserId(pConf.get_ServerType());
                nmn.setDeviceId(pConf.get_ServerName());
            }
            ctx.writeAndFlush(nmn);
            notifyMem.setNotifyMsgHadSended(pUdk, nmn);
            if (nmn.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, nmn);
        }
    }
}