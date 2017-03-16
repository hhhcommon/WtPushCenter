package com.woting.push.core.monitor.socket.netty.sendthread;

import com.woting.push.config.PushConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.socket.netty.NettyHandler;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;

/**
 * 发送控制消息给相应客户端
 * @author wanghui
 *
 */
public class SendControlMsg extends Thread {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    private PushConfig pConf;           //推送配置
    private PushUserUDKey pUdk;         //用户标识
    private ChannelHandlerContext ctx;  //连接上下文

    public SendControlMsg(PushConfig pConf, ChannelHandlerContext ctx) {
        this.pConf=pConf;
        this.ctx=ctx;
        try {
            this.pUdk=ctx.channel().attr(NettyHandler.CHANNEL_PUDKEY).get();
        } catch(Exception e) {}
    }

    @Override
    public void run() {//发送控制消息-到设备
        if (ctx==null||pUdk==null) return;

        Message m=null;
        do {
            try {
                m=globalMem.sendMem.pollDeviceMsgCTL(pUdk, ctx);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            if (m!=null) {
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
    }
}