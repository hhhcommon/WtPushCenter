package com.woting.push.core.monitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.push.config.PushConfig;

/**
 * Tcp连接服务端的监控服务。
 * <pre>
 * 主要用于：
 * 1-注册具体的客户端Socket连接
 * 2-启动Socket连接对应的服务线程
 * 3-关闭时清空相关的连接和内存
 * </pre>
 */
public class TcpSocketServer extends Thread {
    private Logger logger=LoggerFactory.getLogger(TcpSocketServer.class);

    private static int _RUN_STATUS=0;//运行状态，0未启动，1正在启动，2启动成功；3准备停止；4停止
    private PushConfig pc=null;

    /**
     * 构造函数
     * @param pc 推送参数
     */
    public TcpSocketServer(PushConfig pc) {
        super("推送服务监控进程["+pc.get_ControlTcpPort()+"]");
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
        ServerSocket serverSocket=null;
        //启动Socket
        try {
            serverSocket=new ServerSocket(pc.get_ControlTcpPort());
            logger.info("Tcp控制通道服务监控线程启动:地址[{}],端口[{}]", InetAddress.getLocalHost().getHostAddress(), pc.get_ControlTcpPort());
            _RUN_STATUS=2;
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error("启动服务出现异常：\n{}", sw.toString());
        }

        //socket服务的真正监控
        while(_RUN_STATUS==2&&serverSocket!=null&&!serverSocket.isClosed()) {
            try {
                Socket client=serverSocket.accept();
                if (client!=null) {
                    //准备参数
//                    SocketMonitorConfig smc=new SocketMonitorConfig();
//                    new Thread(new SocketHandle(client, smc),"Socket["+client.getRemoteSocketAddress()+",socketKey="+client.hashCode()+"]监控主线程").start();
                }
            } catch(Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                logger.error("启动服务出现异常：\n{}", sw.toString());
            }
        }

        if (_RUN_STATUS==3) {
            
        }
        _RUN_STATUS=4;

   }
}