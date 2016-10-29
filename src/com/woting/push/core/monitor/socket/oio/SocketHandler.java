package com.woting.push.core.monitor.socket.oio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.DateUtils;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.config.SocketHandleConfig;
import com.woting.push.core.monitor.AbstractMoniterServer;
import com.woting.push.core.service.LoadSysCacheService;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;

public class SocketHandler extends AbstractMoniterServer<SocketHandleConfig> {
    private Logger logger=LoggerFactory.getLogger(SocketHandler.class);
    private Socket _socket;
    private BufferedInputStream _socketIn=null;
    private BufferedOutputStream _socketOut=null;

    protected SessionService sessionService=null;
    protected long lastVisitTime=0l;
    private String closeCause="";//关闭socket的原因

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
            sessionService=(SessionService)SpringShell.getBean("sessionService");
            lastVisitTime=System.currentTimeMillis();
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    @Override
    public boolean canContinue() {
        long __period=System.currentTimeMillis()-lastVisitTime;
        if (__period>conf.get_ExpireTime()) {
            long t=System.currentTimeMillis();
            if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="超时关闭:最后访问时间["
              +DateUtils.convert2LocalStr("yyyy-MM-dd HH:mm:ss:SSS", new Date(lastVisitTime))+"("+lastVisitTime+")],当前时间["
              +DateUtils.convert2LocalStr("yyyy-MM-dd HH:mm:ss:SSS", new Date(t))+"("+t+")],过期时间["
              +conf.get_ExpireTime()+"]，超时时长["+__period+"]";
            return false;
        }
        return _socket!=null&&_socket.isBound()&&_socket.isConnected()&&!_socket.isClosed();
    }

    @Override
    public void oneProcess() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void destroyServer() {
        // TODO Auto-generated method stub
        
    }
}
