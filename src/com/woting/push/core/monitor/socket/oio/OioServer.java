package com.woting.push.core.monitor.socket.oio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.PushConfig;
import com.woting.push.PushConstants;
import com.woting.push.core.SocketHandleConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class OioServer extends AbstractLoopMoniter<PushConfig> {
    private Logger logger=LoggerFactory.getLogger(OioServer.class);
    private ServerSocket serverSocket=null;
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    public OioServer(PushConfig pc) {
        super(pc);
        super.setName("TcpSocketServer监控线程[port:"+pc.get_ControlTcpPort()+"]");
    }

    public boolean initServer() {
        try {
            PushConfig pc=(PushConfig)conf;
            serverSocket=new ServerSocket(pc.get_ControlTcpPort());
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
        @SuppressWarnings("unchecked")
        SocketHandleConfig shc=((CacheEle<SocketHandleConfig>)SystemCache.getCache(PushConstants.SOCKETHANDLE_CONF)).getContent();
        SocketHandler sh=new SocketHandler(shc, client);
        sh.setDaemon(true);
        sh.start();
        globalMem.registSocketHandler(sh);
    }
    @Override
    public void destroyServer() {
        //销毁所有消息处理线程
        List<SocketHandler> shL=globalMem.getSochekHanders();
        if (shL!=null&&!shL.isEmpty()) {
            for (SocketHandler sh:shL) {
                synchronized(sh.stopLck) {
                    sh.stopServer();
                    try {
                        sh.stopLck.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            boolean allClosed=false;
            int i=0;
            while (i++<10&&!allClosed) {
                allClosed=true;
                for (SocketHandler sh:shL) {
                    allClosed=sh.isStoped();
                    if (!allClosed) break;
                }
            }
        }
        //销毁总控线程
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