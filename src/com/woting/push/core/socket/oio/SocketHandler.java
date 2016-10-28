package com.woting.push.core.socket.oio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.push.config.SocketHandleConfig;
import com.woting.push.core.monitor.AbstractMoniterServer;

public class SocketHandler extends AbstractMoniterServer<SocketHandleConfig> {
    private Logger logger=LoggerFactory.getLogger(SocketHandler.class);
    private Socket _socket;
    private BufferedInputStream _socketIn=null;
    private BufferedOutputStream _socketOut=null;

    private SessionService sessionService=null;

    protected SocketHandler(SocketHandleConfig conf, Socket socket) {
        super(conf);
        super.setName("Socket["+socket.getRemoteSocketAddress()+"::"+socket.hashCode()+"]监控主线程");
        _socket=socket;
    }

    @Override
    public boolean initServer() {
        try {
            _socket.setTcpNoDelay(true);
            _socketIn=new BufferedInputStream(_socket.getInputStream());
            _socketOut=new BufferedOutputStream(_socket.getOutputStream());
            //创建SessionService对象
            ServletContext sc=(SystemCache.getCache(FConstants.SERVLET_CONTEXT)==null?null:(ServletContext)SystemCache.getCache(FConstants.SERVLET_CONTEXT).getContent());
            if (WebApplicationContextUtils.getWebApplicationContext(sc)!=null) {
                sessionService=(SessionService)WebApplicationContextUtils.getWebApplicationContext(sc).getBean("redisSessionService");
            }
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    @Override
    public void destroyServer() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean canContinue() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void oneProcess() throws Exception {
        // TODO Auto-generated method stub
        
    }

}
