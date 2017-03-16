package com.woting.push.core.monitor.socket.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.push.core.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class MsgEncoder extends MessageToByteEncoder<Object> {
    private Logger logger=LoggerFactory.getLogger(MsgEncoder.class);

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (msg instanceof byte[]) {
            out.writeBytes((byte[])msg);
        } else if (msg instanceof Message) {
            out.writeBytes(((Message)msg).toBytes());
        }
    }

    @Override  
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.info(cause.getMessage());
    }
}