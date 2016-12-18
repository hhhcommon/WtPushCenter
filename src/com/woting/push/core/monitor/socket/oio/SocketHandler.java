package com.woting.push.core.monitor.socket.oio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.DateUtils;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.core.SocketHandleConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

/**
 * 对某一Socket连接的监控服务
 * @author wanghui
 */
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
    private _FatchMsg fatchMsg;
    private _SendMsg sendMsg;

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    public Object stopLck=new Object();
    private Object sendQueueLck=new Object();

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

            setLoopDelay(conf.get_MonitorDelay());//设置监控周期

            //启动下级线程
            receiveMsg=new _ReceiveMsg(socketDesc+"接收信息线程");
            receiveMsg.start();
            logger.debug(socketDesc+"接收信息线程-启动");

            fatchMsg=new _FatchMsg(socketDesc+"获取预发送信息线程");
            fatchMsg.start();
            logger.debug(socketDesc+"获取预发送信息线程-启动");

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
        return socketOk();
    }
    public boolean socketOk() {
        return _socket!=null&&_socket.isBound()&&_socket.isConnected()&&!_socket.isClosed();
    }

    /**
     * 监控这个服务是否健康，主要是子线程是否运行正常
     */
    @Override
    public void oneProcess() throws Exception {
        if (receiveMsg.__isInterrupted||!receiveMsg.__isRunning) {
            sendMsg.__interrupt();
            fatchMsg.__interrupt();
            this._canContinue=false;
        }
        if (fatchMsg.__isInterrupted||!fatchMsg.__isRunning) {
            receiveMsg.__interrupt();
            sendMsg.__interrupt();
            this._canContinue=false;
        }
        if (sendMsg.__isInterrupted||!sendMsg.__isRunning) {
            receiveMsg.__interrupt();
            fatchMsg.__interrupt();
            this._canContinue=false;
        }
    }

    /**
     * 加入一组消息到队列的尾部
     * @param msgList 消息列表
     */
    public void addMessages(List<Message> msgList) {
        if (msgList!=null&&!msgList.isEmpty()) {
            for (Message msg: msgList) {
                try {
                    synchronized(sendQueueLck) {
                        _sendMsgQueue.put(msg.toBytes());
                    }
                } catch(Exception e) {}
            }
        }
    }

    /**
     * 销毁某一Socket监控服务，包括：<br/>
     * 1-停止下级线程服务
     * 2-释放socket相关的资源
     * 3-解除用户和服务的绑定
     */
    @Override
    public void destroyServer() {
        synchronized(stopLck) {
            if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="未知原因";
            logger.debug(socketDesc+"关闭::{}", closeCause);

            //1-停止下级服务
            if (receiveMsg!=null) {try {receiveMsg.__interrupt();} catch(Exception e) {}}
            if (fatchMsg!=null) {try {fatchMsg.__interrupt();} catch(Exception e) {}}
            if (sendMsg!=null) {try {sendMsg.__interrupt();} catch(Exception e) {}}

            boolean canClose=false;
            int loopCount=0;
            while(!canClose) {
                loopCount++;
                if (loopCount>conf.get_TryDestoryAllCount()) {
                    canClose=true;
                    continue;
                }
                if (receiveMsg==null&&receiveMsg==null&&fatchMsg==null) {
                    canClose=true;
                    continue;
                } else {
                    if ((receiveMsg!=null&&!receiveMsg.__isRunning)&&(receiveMsg!=null&&!sendMsg.__isRunning)&&(fatchMsg!=null&&!fatchMsg.__isRunning)) {
                        canClose=true;
                        continue;
                    }
                }
                try { sleep(10); } catch (InterruptedException e) {};
            }
            receiveMsg=null;
            fatchMsg=null;
            sendMsg=null;

            //2-释放Socket的相关资源
            try {
                try {_socketIn.close();} catch(Exception e) {};
                try {_socketOut.close();} catch(Exception e) {};
                try {_socket.close();} catch(Exception e) {};
            } finally {
                _socketIn=null;
                _socketOut=null;
                _socket=null;
            }

            //3-解除用户和服务的绑定
            globalMem.unbindPushUserANDSocket(_pushUserKey, this);
            globalMem.unbindDeviceANDsocket(_pushUserKey, this);
            stopLck.notifyAll();
        }
    }

//================================子线程
    abstract class _LoopThread extends Thread {
        private int continueErrCodunt=0;
        private int sumErrCount=0;

        protected boolean __isInterrupted=false;
        protected boolean __isRunning=true;
        protected _LoopThread(String name) {
            super.setName(name);
        }
        protected void __interrupt(){
            __isInterrupted=true;
        }

        abstract protected void __loopProcess() throws Exception;
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
                while (__isRunning&&!__isInterrupted&&socketOk()) {
                    try {
                        __loopProcess();
                        continueErrCodunt=0;
                    } catch(Exception e) {
                        logger.debug(this.getName()+"运行异常：\n{}", StringUtils.getAllMessage(e));
                        if (e instanceof SocketException) {
                            __isRunning=false;
                        } else {
                            if ( (++continueErrCodunt>=conf.get_Err_ContinueCount())
                                ||(++sumErrCount>=conf.get_Err_SumCount() )) {
                                __isRunning=false;
                             }
                        }
                    }
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
    //接收线程类
    class _ReceiveMsg extends _LoopThread {
        private FileOutputStream fos=null;
        private int _headLen=36;

        private byte[] endMsgFlag={0x00,0x00,0x00};
        private byte[] ba=new byte[2048];//一条消息的内容缓存——最大为2K

        protected _ReceiveMsg(String name) {
            super(name);
        }
        @Override
        protected void __beforeRun() throws Exception {
            if (StringUtils.isNullOrEmptyOrSpace(conf.get_LogPath())) return;
            String filePath=conf.get_LogPath();
            File dir=new File(filePath);
            if (!dir.isDirectory()) dir.mkdirs();
            File f=new File(filePath+"/"+_socket.hashCode()+"_recv.log");
            try {
                if (!f.exists()) f.createNewFile();
                fos=new FileOutputStream(f, false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
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
                            if (((ba[2]&0x80)==0x80)&&((ba[2]&0x00)==0x00)&&(((ba[i-1]&0xF0)==0x00)||((ba[i-1]&0xF0)==0xF0))) isAck=1; else isAck=0;
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

            if (i<3) return;
            if (fos!=null) {
                fos.write(13);
                fos.write(10);
                fos.flush();
            }
            byte[] mba=Arrays.copyOfRange(ba, 0, i);
            if (mba==null||mba.length<3) return; //若没有得到任何内容

            //判断是否是心跳信号
            if (mba.length==3&&mba[0]=='b'&&mba[1]=='^'&&mba[2]=='^') { //如果是发送回执心跳
                byte[] rB=new byte[3];
                rB[0]='B';
                rB[1]='^';
                rB[2]='^';
                synchronized(sendQueueLck) {
                    _sendMsgQueue.add(rB);
                }
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
                            MsgNormal _ms=(MsgNormal)ms;
                            PushUserUDKey _pUdk=PushUserUDKey.buildFromMsg(_ms);
                            if (_pUdk!=null) {
                                if (_ms.getBizType()!=15) {
                                    if (_pUdk.equals(_pushUserKey)) globalMem.receiveMem.addPureMsg(_ms);
                                    else {
                                        if (_ms.getFromType()==1) globalMem.receiveMem.addPureMsg(_ms);
                                    }
                                } else {//是注册消息
                                    boolean bindDeviceFlag=false;
                                    if (_ms.getFromType()==1) {//从服务器来的消息，对于服务器，先到的占用。
                                        _pushUserKey=_pUdk;
                                        bindDeviceFlag=globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, false);
                                        if (bindDeviceFlag) {
                                            globalMem.bindPushUserANDSocket(_pUdk, SocketHandler.this);//处理注册
                                        } else {//与之前的注册不一致，关闭掉这个Socket
                                            MsgNormal ackM=MessageUtils.buildAckMsg(_ms);
                                            ackM.setBizType(15);
                                            ackM.setReturnType(0);//失败
                                            ackM.setSendTime(System.currentTimeMillis());
                                            synchronized(sendQueueLck) {
                                                _sendMsgQueue.add(ackM.toBytes());
                                            }
                                            SocketHandler.this.stopServer();
                                        }
                                    } else {//从客户端来的消息
                                        bindDeviceFlag=globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, false);
                                        if (!bindDeviceFlag) { //与原来记录的不一致，则删除原来的，对于客户端，后到的占用。
                                            SocketHandler _sh=globalMem.getSocketByDevice(_pUdk);
                                            synchronized(_sh.stopLck) {
                                                _sh.stopServer();
                                                try {
                                                    _sh.stopLck.wait();
                                                } catch (InterruptedException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                            globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, true);
                                        }
                                        Map<String, Object> retM=sessionService.dealUDkeyEntry(_pUdk, "socket/entry");
                                        if (!(""+retM.get("ReturnType")).equals("1001")) {
                                            MsgNormal ackM=MessageUtils.buildAckMsg(_ms);
                                            ackM.setBizType(15);
                                            ackM.setReturnType(0);//失败
                                            ackM.setSendTime(System.currentTimeMillis());
                                            synchronized(sendQueueLck) {
                                                _sendMsgQueue.add(ackM.toBytes());
                                            }
                                        } else {//登录成功
                                            _pUdk.setUserId(""+retM.get("UserId"));
                                            _pushUserKey=_pUdk;

                                            globalMem.bindPushUserANDSocket(_pUdk, SocketHandler.this);//处理注册
                                            MsgNormal ackM=MessageUtils.buildAckMsg(_ms);
                                            ackM.setBizType(15);
                                            ackM.setReturnType(1);//成功
                                            ackM.setSendTime(System.currentTimeMillis());
                                            synchronized(sendQueueLck) {
                                                _sendMsgQueue.add(ackM.toBytes());
                                            }
                                            //发送注册成功的消息给组对讲——以便他处理组在线的功能
                                            _ms.setAffirm(0);//设置为不需要回复
                                            _ms.setBizType(1);//设置为组消息
                                            _ms.setCmdType(3);//组通知
                                            _ms.setCommand(0);//进入消息
                                            globalMem.receiveMem.addPureMsg(_ms);
                                            //设置为电话消息
                                            MsgNormal msc=MessageUtils.clone(_ms);
                                            msc.setBizType(2);
                                            globalMem.receiveMem.addPureMsg(msc);
                                        }
                                    }
                                }
                            }
                        } else {//数据流
                            _pushUserKey=globalMem.getPushUserBySocket(SocketHandler.this);
                            if (_pushUserKey!=null) {
                                ((MsgMedia)ms).setExtInfo(_pushUserKey);
                                globalMem.receiveMem.addTypeMsg("media", ms);
                            }
                        }
                        if (fos!=null) {
                            byte[] aa=JsonUtils.objToJson(ms).getBytes();
                            fos.write(aa, 0, aa.length);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        }
                    }
                } catch(Exception e) {
                    logger.debug(StringUtils.getAllMessage(e));
                }
            }
            lastVisitTime=System.currentTimeMillis();
        }
        protected void __close() {
            try { fos.close(); } catch (Exception e) {} finally{ fos=null; }
        }
    }
    //发送线程类
    class _SendMsg extends _LoopThread {
        private FileOutputStream fos=null;

        private byte[] mBytes=null;

        protected _SendMsg(String name) {
            super(name);
        }
        @Override
        protected void __beforeRun() throws Exception {
            if (StringUtils.isNullOrEmptyOrSpace(conf.get_LogPath())) return;
            String filePath=conf.get_LogPath();
            File dir=new File(filePath);
            if (!dir.isDirectory()) dir.mkdirs();
            File f=new File(filePath+"/"+_socket.hashCode()+"_send.log");
            try {
                if (!f.exists()) f.createNewFile();
                fos=new FileOutputStream(f, false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        @Override
        protected void __loopProcess() throws Exception {
            synchronized(_sendMsgQueue) {
                mBytes=_sendMsgQueue.poll();
                if (mBytes==null||mBytes.length<=2) return;
                if (_socketOut!=null&&!_socket.isOutputShutdown()) {
                    _socketOut.write(mBytes);
                    _socketOut.flush();
                    if (fos!=null) {
                        try {
                            fos.write(mBytes);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
        @Override
        protected void __close() {
            //把所有消息发完，才结束
            synchronized(_sendMsgQueue) {
                while (_sendMsgQueue.size()>0) {
                    mBytes=_sendMsgQueue.poll();
                    if (mBytes==null||mBytes.length<=2) continue;
                    try {
                        _socketOut.write(mBytes);
                        _socketOut.flush();
                    } catch (IOException e) {
                        logger.debug(StringUtils.getAllMessage(e));
                    }
                    if (fos!=null) {
                        try {
                            fos.write(mBytes);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        } catch (IOException e) {
                        }
                    }
                }
            }
            try { fos.close(); } catch (Exception e) {} finally{ fos=null; }
        }
    }
    //获取消息类
    class _FatchMsg extends _LoopThread {
        private boolean canAdd=false;
        private FileOutputStream fos=null;

        protected _FatchMsg(String name) {
            super(name);
        }
        @Override
        protected void __beforeRun() throws Exception {
            if (StringUtils.isNullOrEmptyOrSpace(conf.get_LogPath())) return;
            String filePath=conf.get_LogPath();
            File dir=new File(filePath);
            if (!dir.isDirectory()) dir.mkdirs();
            File f=new File(filePath+"/"+_socket.hashCode()+"_sendMsg.log");
            try {
                if (!f.exists()) f.createNewFile();
                fos=new FileOutputStream(f, false);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        @Override
        protected void __loopProcess() throws Exception {
            if (_pushUserKey!=null) {
                canAdd=true;
                //获得控制消息
                Message m=globalMem.sendMem.getUserMsg(_pushUserKey, SocketHandler.this);
                if (m!=null) {
                    long t=System.currentTimeMillis();
                    //根据消息情况，判断该消息是否还需要发送：主要是看是否过期
                    if ((m instanceof MsgMedia)&&(t-m.getSendTime()>60*1000)) canAdd=false;
                    if (canAdd) {
                        try {
                            byte[] aa=JsonUtils.objToJson(m).getBytes();
                            fos.write(aa, 0, aa.length);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        } catch (IOException e) {
                        }
                        _sendMsgQueue.add(m.toBytes());
                    }
                }
                canAdd=true;
                Message nm=globalMem.sendMem.getUserNotifyMsg(_pushUserKey, SocketHandler.this);
                if (nm!=null) {
                    if (nm instanceof MsgMedia) canAdd=false;
                    if (canAdd) {
                        try {
                            byte[] aa=JsonUtils.objToJson(m).getBytes();
                            fos.write(aa, 0, aa.length);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        } catch (IOException e) {
                        }
                        _sendMsgQueue.add(nm.toBytes());
                    }
                }
            }
        }
        @Override
        protected void __close() {
            try { fos.close(); } catch (Exception e) {} finally{ fos=null; }
        }
    }
}