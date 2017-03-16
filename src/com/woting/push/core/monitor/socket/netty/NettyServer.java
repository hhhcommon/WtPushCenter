package com.woting.push.core.monitor.socket.netty;

import java.util.concurrent.TimeUnit;

import com.woting.push.config.PushConfig;
import com.woting.push.core.SocketHandleConfig;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
//import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.timeout.IdleStateHandler;

public class NettyServer {
    PushConfig pc=null;
    SocketHandleConfig sc=null;
    public NettyServer(PushConfig pc, SocketHandleConfig sc) {
        this.pc=pc;
        this.sc=sc;
    }

    public void begin() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup);
            b.channel(NioServerSocketChannel.class);
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    //加入空闲事件，为长连接做准备(In and Out)
                    ch.pipeline().addLast("idleEve", new IdleStateHandler(sc.get_MonitorDelay(), 0, 0, TimeUnit.MILLISECONDS));
                    //写过程(Out)
                    ch.pipeline().addLast("encodeMsg", new MsgEncoder());
                    //读过程(In)
                    ch.pipeline().addLast("delimiterPack", new DelimiterBasedFrameDecoder(1024, false, Unpooled.copiedBuffer("^^".getBytes())));
                    ch.pipeline().addLast("decodePack", new MsgDecoder());
                    ch.pipeline().addLast("bizHandler", new NettyHandler());
                    //处理发送消息事件(In and Out)
                    ch.pipeline().addLast("sendEve", new SendEventHandler());
                }
            });
            b.option(ChannelOption.SO_BACKLOG, 128);
            b.childOption(ChannelOption.SO_KEEPALIVE, true);

            b.bind(pc.get_ControlTcpPort()).sync().channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();  
            bossGroup.shutdownGracefully();  
        }  
    }
}