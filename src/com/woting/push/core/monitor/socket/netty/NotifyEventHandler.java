package com.woting.push.core.monitor.socket.netty;

import com.spiritdata.framework.core.cache.SystemCache;
import com.woting.push.PushConstants;
import com.woting.push.config.PushConfig;
import com.woting.push.core.monitor.socket.netty.sendthread.SendNotifyMsg;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

public class NotifyEventHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent)evt).state()==IdleState.WRITER_IDLE) {//通知消息重发
                new SendNotifyMsg((PushConfig)SystemCache.getCache(PushConstants.PUSH_CONF).getContent(), ctx).start();
            }
        }
    }
}