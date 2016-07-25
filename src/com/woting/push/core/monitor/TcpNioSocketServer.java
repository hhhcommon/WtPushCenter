package com.woting.push.core.monitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.push.config.PushConfig;

public class TcpNioSocketServer extends Thread {
    private Logger logger=LoggerFactory.getLogger(TcpNioSocketServer.class);

    private static int _RUN_STATUS=0;//运行状态，0未启动，1正在启动，2启动成功；3准备停止；4停止
    private PushConfig pc=null;

    private Selector selector;
    private ServerSocketChannel serverChannel=null;

    /**
     * 构造函数
     * @param pc 推送参数
     */
    public TcpNioSocketServer(PushConfig pc) {
        super("推送服务监控进程["+pc.getControlTcpPort()+"]");
        this.pc=pc;
    }

    /**
     * 获得运行状态
     */
    public int getRUN_STATUS() {
        return _RUN_STATUS;
    }

    /**
     * 停止Sever过程
     */
    public void stopServer() {
        _RUN_STATUS=3;
    }

    //主Tcp服务过程
    public void run() {
        _RUN_STATUS=1;
        //启动Socket
        try {
            selector=Selector.open();

            serverChannel=ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().setReuseAddress(true);
            serverChannel.socket().bind(new InetSocketAddress(pc.getControlTcpPort()));
            logger.info("NIO TCP Connector nio 供应类: {}", selector.provider().getClass().getCanonicalName());
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error("启动服务出现异常：\n{}", sw.toString());
        }
        _RUN_STATUS=2;

        //socket服务的真正监控
        while(_RUN_STATUS==2&&selector!=null) {
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
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                cse.printStackTrace(pw);
                logger.error("启动服务出现异常：\n{}", sw.toString());
                //若selector已关闭，则退出监控
                break;
            } catch(Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.error("启动服务出现异常：\n{}", sw.toString());
            }
        }

        //停止监控过程
        if (selector!=null) {
            try {
                selector.wakeup();
                selector.close();
            } catch(Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.error("启动服务出现异常：\n{}", sw.toString());
            } finally {
                selector=null;
            }
        }
        if (serverChannel!=null) {
            try {
                serverChannel.close();
            } catch(Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.error("启动服务出现异常：\n{}", sw.toString());
            } finally {
                serverChannel=null;
            }
        }
        _RUN_STATUS=4;
    }
}