package com.woting.push.core.monitor.socket.netty.sendthread;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.monitor.socket.netty.NettyHandler;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;

public class ResendNeedCtrAffirmMsg extends Thread {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private PushUserUDKey pUdk;         //用户标识
    private ChannelHandlerContext ctx;  //连接上下文

    public ResendNeedCtrAffirmMsg(ChannelHandlerContext ctx) {
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {//发送控制消息-到设备
        if (ctx==null||pUdk==null) return;

        LinkedBlockingQueue<Map<String, Object>> mmq=globalMem.sendMem.getSendedNeedCtlAffirmMsg(pUdk, ctx);
        while (mmq!=null&&!mmq.isEmpty()) {
            Map<String, Object> _m=mmq.poll();
            if (_m==null||_m.isEmpty()) continue;
            Message _msg=(Message)_m.get("message");
            if (_msg==null) continue;
            ctx.writeAndFlush(_msg);
            globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, _msg);
        }
    }
}