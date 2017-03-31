package com.woting.push.core.monitor.socket.netty.sendthread;

import com.woting.audioSNS.mediaflow.MediaConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.monitor.socket.netty.NettyHandler;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;

/**
 * 发送媒体数据消息给相应客户端
 * @author wanghui
 *
 */
public class SendMediaMsg extends Thread {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    private MediaConfig mConf;          //媒体消息配置
    private PushUserUDKey pUdk;         //用户标识
    private ChannelHandlerContext ctx;  //连接上下文

    public SendMediaMsg(MediaConfig mConf, ChannelHandlerContext ctx) {
        this.mConf=mConf;
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {//发送媒体消息
        if (ctx==null||pUdk==null) return;

        Message m=null;
        do {
            try {
                m=globalMem.sendMem.pollDeviceMsgMDA(pUdk, ctx);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            if (m!=null) {
                try {//传消息
                    if (m instanceof MsgMedia) {
                        MsgMedia mm=(MsgMedia)m;
                        if (System.currentTimeMillis()-mm.getSendTime()<(mm.getMediaType()==1?mConf.get_AudioExpiredTime():mConf.get_VedioExpiredTime())) {
                            ctx.writeAndFlush(m);
                        }
                    }
                } catch(Exception e) {}
            }
        } while (m!=null);
    }
}