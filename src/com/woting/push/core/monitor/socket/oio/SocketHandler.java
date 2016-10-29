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
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;

public class SocketHandler extends AbstractLoopMoniter<SocketHandleConfig> {
    private Logger logger=LoggerFactory.getLogger(SocketHandler.class);
    private Socket _socket;
    private BufferedInputStream _socketIn=null;
    private BufferedOutputStream _socketOut=null;

    private SessionService sessionService=null;
    private long lastVisitTime=0l;
    private String closeCause="";//关闭socket的原因
    private String socketDesc=null;

    private _ReceiveMsg receiveMsg;
    private _SendMsg sendMsg;

    protected SocketHandler(SocketHandleConfig conf, Socket socket) {
        super(conf);
        super.setName("Socket["+socket.getRemoteSocketAddress()+"::"+socket.hashCode()+"]监控主线程");
        socketDesc="Socket["+socket.getRemoteSocketAddress()+"::"+socket.hashCode()+"]";
        _socket=socket;
        
    }

    @Override
    public boolean initServer() {
        try {
            _socket.setTcpNoDelay(true);
            _socketIn=new BufferedInputStream(_socket.getInputStream());
            _socketOut=new BufferedOutputStream(_socket.getOutputStream());
            if (_socketIn==null) throw new NullPointerException("输入流");
            if (_socketOut==null) throw new NullPointerException("输出流");
            //创建SessionService对象
            sessionService=(SessionService)SpringShell.getBean("sessionService");
            lastVisitTime=System.currentTimeMillis();

            //启动下级线程
            receiveMsg=new _ReceiveMsg(socketDesc+"接收信息线程");
            receiveMsg.start();
            logger.debug(socketDesc+"接收信息线程-启动");
            sendMsg=new _SendMsg(socketDesc+"发送信息线程");
            sendMsg.start();
            logger.debug(socketDesc+"发送信息线程-启动");
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
    }

    @Override
    public void destroyServer() {
    }

    abstract class _LoopThread extends Thread {
        private boolean __isInterrupted=false;
        private boolean __isRunning=true;
        protected _LoopThread(String name) {
            super.setName(name);
        }
        protected void __interrupt(){
            __isInterrupted=true;
            this.interrupt();
            super.interrupt();
        }

        abstract protected void __loopProcess() throws Exception;
        abstract protected boolean __continue();
        abstract protected void __beforeRun() throws Exception;

        public void run() {
            try {
                __beforeRun();
                this.__isRunning=true;
            } catch(Exception e) {
                this.__isRunning=false;
                logger.debug(this.getName()+"运行异常：\n{}", StringUtils.getAllMessage(e));
                if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="<"+this.getName()+">运行异常关闭:"+e.getClass().getName()+" | "+e.getMessage();
            }
            try {
                while (__continue()&&__isRunning&&!__isInterrupted) {
                    __loopProcess();
                }
            } catch(Exception e) {
                logger.debug(this.getName()+"运行异常：\n{}", StringUtils.getAllMessage(e));
                if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="<"+this.getName()+">运行异常关闭:"+e.getClass().getName()+" | "+e.getMessage();
            } finally {
                __isRunning=false;
            }
        }
    }
    class _ReceiveMsg extends _LoopThread {
        protected _ReceiveMsg(String name) {
            super(name);
        }
        @Override
        protected void __loopProcess() throws Exception {
            
        }
        @Override
        protected boolean __continue() {
            // TODO Auto-generated method stub
            return false;
        }
        @Override
        protected void __beforeRun() throws Exception {
            // TODO Auto-generated method stub
            
        }
    }
    class _SendMsg extends _LoopThread {
        protected _SendMsg(String name) {
            super(name);
        }
        @Override
        protected void __loopProcess() throws Exception {
            
        }
        @Override
        protected boolean __continue() {
            // TODO Auto-generated method stub
            return false;
        }
        @Override
        protected void __beforeRun() throws Exception {
            // TODO Auto-generated method stub
            
        }
    }
}