package com.woting.push.core.monitor.socket.netty.sendthread;

import java.util.List;

import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MsgNormal;
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

        List<MsgNormal> mList=globalMem.sendMem.getSendedNeedCtlAffirmMsgANDSend(pUdk, ctx);
        if (mList!=null&&!mList.isEmpty()) {
            for (int i=0; i<mList.size(); i++) ctx.writeAndFlush(mList.get(i));
        }
    }
}