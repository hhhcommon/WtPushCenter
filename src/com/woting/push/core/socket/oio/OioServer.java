package com.woting.push.core.socket.oio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.StringUtils;
import com.woting.push.config.PushConfig;
import com.woting.push.core.monitor.AbstractMoniterServer;

public class OioServer extends AbstractMoniterServer<PushConfig> {
    private Logger logger=LoggerFactory.getLogger(OioServer.class);
    private ServerSocket serverSocket=null;

    public OioServer(PushConfig pc) {
        super(pc);
        super.setName("TcpSocketServer监控线程[port:"+pc.get_ControlTcpPort()+"]");
    }

    public boolean initServer() {
        try {
            PushConfig pc=(PushConfig)conf;
            serverSocket=new ServerSocket(pc.get_ControlTcpPort());
            logger.info("Tcp控制通道服务监控线程启动:地址[{}],端口[{}]", InetAddress.getLocalHost().getHostAddress(), pc.get_ControlTcpPort());
            //加一个关闭jvm时可调用的方法，关闭此线程池
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        System.out.println("JVM退出时关闭推送服务监控进程");
                        destroyServer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            return true;
        } catch (IOException e) {
            logger.error("启动服务出现异常：\n{}", StringUtils.getAllMessage(e));
            return false;
        }
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    @Override
    public void oneProcess() throws Exception {
        Socket client=serverSocket.accept();//获得连接
//        //准备参数
//        SocketMonitorConfig smc=new SocketMonitorConfig();
//        new Thread(new SocketHandle(client, smc),"Socket["+client.getRemoteSocketAddress()+",socketKey="+client.hashCode()+"]监控主线程").start();
    }
    @Override
    public void destroyServer() {
        if (serverSocket!=null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
            } finally {
                serverSocket=null;
            }
        }
    }
}