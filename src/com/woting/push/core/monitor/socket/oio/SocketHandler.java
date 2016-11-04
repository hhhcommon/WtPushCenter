package com.woting.push.core.monitor.socket.oio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.DateUtils;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.config.SocketHandleConfig;
import com.woting.push.core.mem.TcpGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

public class SocketHandler extends AbstractLoopMoniter<SocketHandleConfig> {
    private Logger logger=LoggerFactory.getLogger(SocketHandler.class);
    private Socket _socket;
    private BufferedInputStream _socketIn=null;
    private BufferedOutputStream _socketOut=null;
    private PushUserUDKey _pushUserKey=null;//和这个Socket绑定的用户

    protected ArrayBlockingQueue<byte[]> _sendMsgQueue=new ArrayBlockingQueue<byte[]>(512); //512条待发布消息的缓存

    private SessionService sessionService=null;

    private long lastVisitTime=0l;
    private String closeCause="";//关闭socket的原因
    private String socketDesc=null;

    private _ReceiveMsg receiveMsg;
    private _SendMsg sendMsg;

    private TcpGlobalMemory globlalMem=TcpGlobalMemory.getInstance();

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

            setLoopDelay(conf.get_MonitorDelay());

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
        if (receiveMsg.__isInterrupted||!receiveMsg.__isRunning) {
            sendMsg.__interrupt();
            this._canContinue=false;
        }
        if (sendMsg.__isInterrupted||!sendMsg.__isRunning) {
            receiveMsg.__interrupt();
            this._canContinue=false;
        }
    }

    @Override
    public void destroyServer() {
        if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="未知原因";
        logger.debug(socketDesc+"关闭::{}", closeCause);

        if (receiveMsg!=null) {try {receiveMsg.__interrupt();} catch(Exception e) {}}
        if (sendMsg!=null) {try {sendMsg.__interrupt();} catch(Exception e) {}}

        boolean canClose=false;
        int loopCount=0;
        while(!canClose) {
            loopCount++;
            if (loopCount>conf.get_TryDestoryAllCount()) {
                canClose=true;
                continue;
            }
            if (!receiveMsg.__isRunning&&!sendMsg.__isRunning) {
                canClose=true;
                continue;
            }
            try { sleep(10); } catch (InterruptedException e) {};
        }

        try {
            try {_socketIn.close();} catch(Exception e) {};
            try {_socketOut.close();} catch(Exception e) {};
            try {_socket.close();} catch(Exception e) {};
        } finally {
            receiveMsg=null;
            sendMsg=null;
            _socketIn=null;
            _socketOut=null;
            _socket=null;
        }
    }

//================================子线程
    abstract class _LoopThread extends Thread {
        protected boolean __isInterrupted=false;
        protected boolean __isRunning=true;
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
        abstract protected void __close();

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
                    try { sleep(10); } catch (InterruptedException e) {};
                }
            } catch(Exception e) {
                logger.debug(this.getName()+"运行异常：\n{}", StringUtils.getAllMessage(e));
                if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="<"+this.getName()+">运行异常关闭:"+e.getClass().getName()+" | "+e.getMessage();
            } finally {
                __close();
                __isRunning=false;
            }
        }
    }
    class _ReceiveMsg extends _LoopThread {
        private int continueErrCodunt=0;
        private int sumErrCount=0;
        private FileOutputStream fos=null;
        private int _headLen=36;

        private byte[] endMsgFlag={0x00,0x00,0x00};
        private byte[] ba=new byte[2048];//一条消息的内容缓存——最大为2K

        protected _ReceiveMsg(String name) {
            super(name);
        }
        @Override
        protected void __loopProcess() throws Exception {//处理一条信息
            endMsgFlag[0]=0x00;
            endMsgFlag[1]=0x00;
            endMsgFlag[2]=0x00;

            int msgType=-1, r=-1, i=0, isAck=-1, isRegist=0;
            short _dataLen=-3;

            boolean hasBeginMsg=false; //是否开始了一个消息
            while ((r=_socketIn.read())!=-1) {//读取一条消息
                if (fos!=null) fos.write(r);
                ba[i++]=(byte)r;
                endMsgFlag[0]=endMsgFlag[1];
                endMsgFlag[1]=endMsgFlag[2];
                endMsgFlag[2]=(byte)r;
                if (!hasBeginMsg) {
                    if (endMsgFlag[0]=='b'&&endMsgFlag[1]=='^'&&endMsgFlag[2]=='^') {
                        break;//是心跳消息
                    } else if ((endMsgFlag[0]=='|'&&endMsgFlag[1]=='^')||(endMsgFlag[0]=='^'&&endMsgFlag[1]=='|')) {
                        hasBeginMsg=true;
                        ba[0]=endMsgFlag[0];
                        ba[1]=endMsgFlag[1];
                        ba[2]=endMsgFlag[2];
                        i=3;
                        continue;
                    } else if ((endMsgFlag[1]=='|'&&endMsgFlag[2]=='^')||(endMsgFlag[1]=='^'&&endMsgFlag[2]=='|')) {
                        hasBeginMsg=true;
                        ba[0]=endMsgFlag[1];
                        ba[1]=endMsgFlag[2];
                        i=2;
                        continue;
                    }
                    if (i>2) {
                        for (int n=1;n<=i;n++) ba[n-1]=ba[n];
                        --i;
                    }
                } else {
                    if (msgType==-1) msgType=MessageUtils.decideMsg(ba);
                    if (msgType==0) {//0=控制消息(一般消息)
                        if (isAck==-1&&i==12) {
                            if (((ba[2]&0x80)==0x80)&&((ba[2]&0x00)==0x00)&&((ba[i-1]&0xF0)==0x00)) isAck=1; else isAck=0;
                            if ((ba[i-1]&0xF0)==0xF0) isRegist=1;
                        } else  if (isAck==1) {//是回复消息
                            if (isRegist==1) { //是注册消息
                                if (i==48&&endMsgFlag[2]==0) _dataLen=80; else _dataLen=91;
                                if (_dataLen>=0&&i==_dataLen) break;
                            } else { //非注册消息
                                if (_dataLen<0) _dataLen=45;
                                if (_dataLen>=0&&i==_dataLen) break;
                            }
                        } else  if (isAck==0) {//是一般消息
                            if (isRegist==1) {//是注册消息
                                if (((ba[2]&0x80)==0x80)&&((ba[2]&0x00)==0x00)) {
                                    if (i==48&&endMsgFlag[2]==0) _dataLen=80; else _dataLen=91;
                                } else {
                                    if (i==47&&endMsgFlag[2]==0) _dataLen=79; else _dataLen=90;
                                }
                                if (_dataLen>=0&&i==_dataLen) break;
                            } else {//非注册消息
                                if (_dataLen==-3&&endMsgFlag[1]=='^'&&endMsgFlag[2]=='^') _dataLen++;
                                else if (_dataLen>-3&&_dataLen<-1) _dataLen++;
                                else if (_dataLen==-1) {
                                    _dataLen=(short)(((endMsgFlag[2]<<8)|endMsgFlag[1]&0xff));
                                    if (_dataLen==0) break;
                                } else if (_dataLen>=0) {
                                    if (--_dataLen==0) break;
                                }
                            }
                        }
                    } else if (msgType==1) {//1=媒体消息
                        if (isAck==-1) {
                            if (((ba[2]&0x80)==0x80)&&((ba[2]&0x40)==0x00)) isAck=1; else isAck=0;
                        } else if (isAck==1) {//是回复消息
                            if (i==_headLen+1) break;
                        } else if (isAck==0) {//是一般媒体消息
                            if (i==_headLen+2) _dataLen=(short)(((ba[_headLen+1]<<8)|ba[_headLen]&0xff));
                            if (_dataLen>=0&&i==_dataLen+_headLen+2) break;
                        }
                    }
                }
            }//一条消息读取完成
            fos.flush();
            byte[] mba=Arrays.copyOfRange(ba, 0, i);
            if (mba==null||mba.length<3) return; //若没有得到任何内容

            //判断是否是心跳信号
            if (mba.length==3&&mba[0]=='b'&&mba[1]=='^'&&mba[2]=='^') { //如果是发送回执心跳
                byte[] rB=new byte[3];
                rB[0]='B';
                rB[1]='^';
                rB[2]='^';
                _sendMsgQueue.add(rB);
            } else { //处理正常消息
                try {
                    Message ms=null;
                    try {
                        ms=MessageUtils.buildMsgByBytes(mba);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    logger.debug("收到消息::"+JsonUtils.objToJson(ms));

                    if (ms!=null&&!ms.isAck()) {
                        if (ms instanceof MsgNormal) {
                            PushUserUDKey _puUdk=PushUserUDKey.buildFromMsg(ms);
                            //处理注册
                            Map<String, Object> retM=sessionService.dealUDkeyEntry(_puUdk, "socket/entry");
                            if (!(""+retM.get("ReturnType")).equals("1001")) {
                                MsgNormal ackM=MessageUtils.buildAckMsg((MsgNormal)ms);
                                ackM.setBizType(15);
                                ackM.setPCDType(((MsgNormal)ms).getPCDType());
                                ackM.setUserId(((MsgNormal)ms).getUserId());
                                ackM.setIMEI(((MsgNormal)ms).getIMEI());
                                ackM.setReturnType(0);//失败
                                _sendMsgQueue.add(ackM.toBytes());
                            } else {//登录成功
                                _puUdk.setUserId(""+retM.get("UserId"));
                                if (((MsgNormal)ms).getBizType()==15) {//是注册消息
                                    MsgNormal ackM=MessageUtils.buildAckMsg((MsgNormal)ms);
                                    ackM.setBizType(15);
                                    ackM.setPCDType(((MsgNormal)ms).getPCDType());
                                    ackM.setUserId(((MsgNormal)ms).getUserId());
                                    ackM.setIMEI(((MsgNormal)ms).getIMEI());
                                    ackM.setReturnType(1);//成功
                                    _sendMsgQueue.add(ackM.toBytes());
                                    globlalMem.bindPushUserANDSocket(_pushUserKey, SocketHandler.this);
                                } else {//是非注册消息
                                    _pushUserKey=_puUdk;
                                    globlalMem.receiveMem.addPureMsg(ms);
                                }
                            }
                        } else {//数据流
                            ((MsgMedia)ms).setExtInfo(_pushUserKey.toHashMapAsBean());
                            globlalMem.receiveMem.addPureMsg(ms);
                        }
                    }
                } catch(Exception e) {
                    logger.debug(StringUtils.getAllMessage(e));
                }
            }
            lastVisitTime=System.currentTimeMillis();
        }
        @Override
        protected boolean __continue() {
            return true;
        }
        @Override
        protected void __beforeRun() throws Exception {
            String filePath="C:/opt/logs/receiveLogs";
            File dir=new File(filePath);
            if (!dir.isDirectory()) dir.mkdirs();
            File f=new File(filePath+"/"+_socket.hashCode()+".log");
            try {
                if (!f.exists()) f.createNewFile();
                fos=new FileOutputStream(f, false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        protected void __close() {
            try { fos.close(); } catch (Exception e) {} finally{ fos=null; }
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
            return true;
        }
        @Override
        protected void __beforeRun() throws Exception {
        }
        @Override
        protected void __close() {
            // TODO Auto-generated method stub
            
        }
    }
}