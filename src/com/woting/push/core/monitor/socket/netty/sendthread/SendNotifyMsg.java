package com.woting.push.core.monitor.socket.netty.sendthread;

import java.util.List;

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
        List<MsgNormal> notifyMsgList=notifyMem.getNeedSendNotifyMsg(pUdk);
        if (notifyMsgList!=null&&!notifyMsgList.isEmpty()) {
            for (MsgNormal mn: notifyMsgList) {
                if (mn.getFromType()==0) {
                    mn.setUserId(pConf.get_ServerType());
                    mn.setDeviceId(pConf.get_ServerName());
                }
                ctx.writeAndFlush(mn);
                notifyMem.setNotifyMsgHadSended(pUdk, mn);
                if (mn.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(pUdk, mn);
            }
        }
    }
}