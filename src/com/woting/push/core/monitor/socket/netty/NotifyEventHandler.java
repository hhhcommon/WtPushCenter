package com.woting.push.core.monitor.socket.netty;

import com.woting.push.core.monitor.socket.netty.sendthread.ResendNotifyMsg;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class NotifyEventHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent)evt).state()==IdleState.WRITER_IDLE) {//通知消息重发
                new ResendNotifyMsg(ctx).start();
            }
        }
    }
}