package com.woting.push.core.monitor.socket.netty;

import java.util.List;

import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

public class MsgDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] _data = new byte[in.readableBytes()];
        in.readBytes(_data);
        Message m=MessageUtils.buildMsgByBytes(_data);
        out.add(m==null?_data:m);
    }
}