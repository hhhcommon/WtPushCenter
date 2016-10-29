package com.woting.push.core.monitor.socket.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.StringUtils;
import com.woting.push.config.PushConfig;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class NioServer extends AbstractLoopMoniter<PushConfig> {
    private Logger logger=LoggerFactory.getLogger(NioServer.class);

    private Selector selector;
    private ServerSocketChannel serverChannel=null;

    public NioServer(PushConfig conf) {
        super(conf);
    }

    @Override
    public boolean initServer() {
        //启动Socket
        try {
            selector=Selector.open();

            serverChannel=ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().setReuseAddress(true);
            serverChannel.socket().bind(new InetSocketAddress(conf.get_ControlTcpPort()));
            logger.info("NIO TCP Connector nio 供应类: {}", selector.provider().getClass().getCanonicalName());
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            logger.error("启动服务出现异常：\n{}", StringUtils.getAllMessage(e));
        }
        return false;
    }

    @Override
    public boolean canContinue() {
        return selector!=null;
    }

    @Override
    public void oneProcess() {
        try {
            if(selector.select()>0) {
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key=it.next();
                    it.remove();
                    if (key.isAcceptable()) {
                        //获得客户端连接通道
                        SocketChannel clientChannel=((ServerSocketChannel)key.channel()).accept();
                        clientChannel.configureBlocking(false);
                        clientChannel.register(selector, SelectionKey.OP_READ);
                        clientChannel.register(selector, SelectionKey.OP_WRITE);
                    }
                }
            }
        } catch(ClosedSelectorException cse) {
            logger.error("启动服务出现异常：\n{}", StringUtils.getAllMessage(cse));
            //若selector已关闭，则退出监控
        } catch(Exception e) {
            logger.error("启动服务出现异常：\n{}", StringUtils.getAllMessage(e));
        }
    }

    @Override
    public void destroyServer() {
        if (selector!=null) {
            try {
                selector.wakeup();
                selector.close();
            } catch(Exception e) {
                logger.error("启动服务出现异常：\n{}", StringUtils.getAllMessage(e));
            } finally {
                selector=null;
            }
        }
        if (serverChannel!=null) {
            try {
                serverChannel.close();
            } catch(Exception e) {
                logger.error("启动服务出现异常：\n{}", StringUtils.getAllMessage(e));
            } finally {
                serverChannel=null;
            }
        }
    }
}