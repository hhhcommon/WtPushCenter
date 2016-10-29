package com.woting.push.core.monitor.socket.oio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.PushConstants;
import com.woting.push.config.PushConfig;
import com.woting.push.config.SocketHandleConfig;
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
            final Thread thisT=Thread.currentThread();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    logger.info("正在关闭TcpSocketServer监控线程[port:"+pc.get_ControlTcpPort()+"]...");
                    destroyServer();
                    try{
                        thisT.join();
                        logger.info("服务已关闭");
                    }catch(Exception e){
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
        SocketHandleConfig shc=((CacheEle<SocketHandleConfig>)SystemCache.getCache(PushConstants.SOCKETHANDLE_CONF)).getContent();
        
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