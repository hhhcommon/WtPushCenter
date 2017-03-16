package com.woting.push.core.monitor.socket.netty;

import com.spiritdata.framework.core.cache.SystemCache;
import com.woting.push.PushConstants;
import com.woting.push.config.MediaConfig;
import com.woting.push.config.PushConfig;
import com.woting.push.core.monitor.socket.netty.event.SendMsgEvent;
import com.woting.push.core.monitor.socket.netty.sendthread.SendControlMsg;
import com.woting.push.core.monitor.socket.netty.sendthread.SendMediaMsg;
import com.woting.push.core.monitor.socket.netty.sendthread.SendNotifyMsg;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 * 处理发送事件的Handler
 *
 * 用户事件触发发送过程，注意这个过程要在处理消息的Handler链条之后
 * @author wanghui
 */
public class SendEventHandler extends ChannelDuplexHandler {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SendMsgEvent) {
            switch (((SendMsgEvent)evt).sendMsgType()) {
            case SEND_CONTROL:
                new SendControlMsg((PushConfig)SystemCache.getCache(PushConstants.PUSH_CONF).getContent(), ctx).start();
                break;
            case SEND_MEDIA:
                new SendMediaMsg((MediaConfig)SystemCache.getCache(PushConstants.MEDIA_CONF).getContent(), ctx).start();
                break;
            case SEND_NOTIFY:
                new SendNotifyMsg((PushConfig)SystemCache.getCache(PushConstants.PUSH_CONF).getContent(), ctx).start();
                break;
            default: break;
            }
        }
    }
}