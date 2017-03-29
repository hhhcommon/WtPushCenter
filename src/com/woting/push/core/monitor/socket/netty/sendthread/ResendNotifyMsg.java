package com.woting.push.core.monitor.socket.netty.sendthread;

import java.util.List;

import com.woting.audioSNS.notify.mem.NotifyMemory;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.socket.netty.NettyHandler;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;

public class ResendNotifyMsg extends Thread {
    private NotifyMemory notifyMem=NotifyMemory.getInstance();
    private PushUserUDKey pUdk;         //用户标识
    private ChannelHandlerContext ctx;  //连接上下文

    public ResendNotifyMsg(ChannelHandlerContext ctx) {
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {//发送控制消息-到设备
        if (ctx==null||pUdk==null) return;

        //发送已发送过的通知消息
        List<MsgNormal> notifyMsgList=notifyMem.getNeedSendNotifyMsg(pUdk);
        if (notifyMsgList!=null&&!notifyMsgList.isEmpty()) {
            for (MsgNormal mn: notifyMsgList) {
                ctx.writeAndFlush(mn);
                notifyMem.setNotifyMsgHadSended(pUdk, mn);
            }
        }
    }
}