package com.woting.push.core.monitor.socket.oio;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.DateUtils;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.core.SocketHandleConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
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
    protected String _deviceKey=null;//和这个Socket绑定的用户
    private PushUserUDKey _pushUserKey=null;//和这个Socket绑定的用户
    public PushUserUDKey getPuUDKey() {
        return _pushUserKey;
    }

    protected ArrayBlockingQueue<byte[]> _sendMsgQueue=new ArrayBlockingQueue<byte[]>(512); //512条待发布消息的缓存
    protected ArrayBlockingQueue<Message> _recvMsgQueue=new ArrayBlockingQueue<Message>(512); //512条待处理消息的缓存

    private SessionService sessionService=null;

    private long lastVisitTime=0l;
    private String closeCause="";//关闭socket的原因
    private String socketDesc=null;

    private _ReceiveMsg receiveMsg;
    private _DealMsg dealMsg;
    private _FatchMsg fatchMsg;
    private _SendMsg sendMsg;

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    public volatile Object stopLck=new Object();
    private volatile boolean canRunning=true; //是否可以运行

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
            dealMsg=new _DealMsg(socketDesc+"处理消息体线程");
            dealMsg.start();
            logger.debug(socketDesc+"处理消息体线程-启动");

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
        return socketOk()&&canRunning;
    }
    public boolean socketOk() {
        return _socket!=null&&_socket.isBound()&&_socket.isConnected()&&!_socket.isClosed();
    }

    /**
     * 监控这个服务是否健康，主要是子线程是否运行正常
     */
    @Override
    public void oneProcess() throws Exception {
        if (dealMsg.__isInterrupted||!dealMsg.__isRunning) {
            receiveMsg.__interrupt();
            sendMsg.__interrupt();
            fatchMsg.__interrupt();
            canRunning=false;
        }

        if (receiveMsg.__isInterrupted||!receiveMsg.__isRunning) {
            dealMsg.__interrupt();
            sendMsg.__interrupt();
            fatchMsg.__interrupt();
            canRunning=false;
        }
        if (fatchMsg.__isInterrupted||!fatchMsg.__isRunning) {
            dealMsg.__interrupt();
            receiveMsg.__interrupt();
            sendMsg.__interrupt();
            canRunning=false;
        }
        if (sendMsg.__isInterrupted||!sendMsg.__isRunning) {
            dealMsg.__interrupt();
            receiveMsg.__interrupt();
            fatchMsg.__interrupt();
            canRunning=false;
        }
    }

    /**
     * 加入一组消息到队列的尾部
     * @param msgList 消息列表
     */
    public void addMessages(List<Message> msgList) {
        if (msgList!=null&&!msgList.isEmpty()) {
            for (Message msg: msgList) {
                try { _sendMsgQueue.put(msg.toBytes()); } catch(Exception e) {}
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
            try {
                if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="未知原因";
                logger.debug(socketDesc+"关闭::{}", closeCause);

                //1-停止下级服务
                if (dealMsg!=null) {try {dealMsg.__interrupt();} catch(Exception e) {}}
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
                    if (dealMsg==null&&receiveMsg==null&&receiveMsg==null&&fatchMsg==null) {
                        canClose=true;
                        continue;
                    } else {
                        if ((dealMsg!=null&&!dealMsg.__isRunning)&&(receiveMsg!=null&&!receiveMsg.__isRunning)&&(receiveMsg!=null&&!sendMsg.__isRunning)&&(fatchMsg!=null&&!fatchMsg.__isRunning)) {
                            canClose=true;
                            continue;
                        }
                    }
                    try { sleep(10); } catch (InterruptedException e) {};
                }
                dealMsg=null;
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
                if (!StringUtils.isNullOrEmptyOrSpace(_deviceKey)) {
                    PushUserUDKey _tpUdk=new PushUserUDKey();
                    String[] sp=_deviceKey.split("::");
                    _tpUdk.setDeviceId(sp[0]);
                    _tpUdk.setPCDType(Integer.parseInt(sp[1]));
                    globalMem.unbindDeviceANDsocket(_tpUdk, this);
                }
            } finally {
                stopLck.notifyAll();
            }
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
                while (__isRunning&&!__isInterrupted&&socketOk()&&canRunning) {
                    try {
                        __loopProcess();
                        continueErrCodunt=0;
                    } catch(Exception e) {
                        logger.debug(this.getName()+"运行异常：\n{}", StringUtils.getAllMessage(e));
                        if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="<"+this.getName()+">运行异常关闭:"+e.getClass().getName()+" | "+e.getMessage();
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

    //处理接收消息线程类
    class _DealMsg extends _LoopThread {
        private Message msg;
        protected _DealMsg(String name) {
            super(name);
        }
        @Override
        protected void __beforeRun() throws Exception {
        }
        @Override
        protected void __loopProcess() throws Exception {
//            synchronized(_recvMsgQueue) {
//            }
            msg=_recvMsgQueue.poll();
            if (msg==null) return;
            try {
                __dealOneMsg(msg);
            } catch(Exception e) {
                logger.debug(StringUtils.getAllMessage(e));
            }
        }
        @Override
        protected void __close() {
            //把所有消息发完，才结束
//            synchronized(_recvMsgQueue) {
//            }
            while (_recvMsgQueue.size()>0) {
                msg=_recvMsgQueue.poll();
                if (msg==null) continue;
                try {
                    __dealOneMsg(msg);
                } catch (Exception e) {
                    logger.debug(StringUtils.getAllMessage(e));
                }
            }
        }
        private void __dealOneMsg(Message _msg) {
            if (_msg!=null&&!_msg.isAck()) {
                if (_msg instanceof MsgNormal) {
                    MsgNormal _ms=(MsgNormal)_msg;
                    PushUserUDKey _pUdk=PushUserUDKey.buildFromMsg(_ms);
                    if (_pUdk!=null) {
                        if (_ms.getBizType()!=15) {
                            if (_pUdk.equals(_pushUserKey)) globalMem.receiveMem.addPureMsg(_ms);
                            else {
                                MsgNormal noLogMsg=MessageUtils.buildAckMsg((MsgNormal)_ms);
                                noLogMsg.setCmdType(1);
                                try { _sendMsgQueue.add(noLogMsg.toBytes()); } catch(Exception e) {}
                            }
                        } else {//是注册消息
                            boolean bindDeviceFlag=false;
                            if (_ms.getFromType()==1) {//从服务器来的消息，对于服务器，先到的占用。
                                _pushUserKey=_pUdk;
                                bindDeviceFlag=globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, false);
                                if (bindDeviceFlag) {
                                    globalMem.bindPushUserANDSocket(_pUdk, SocketHandler.this);//处理注册
                                    _deviceKey=_pUdk.getDeviceId()+"::"+_pUdk.getPCDType();
                                } else {//与之前的注册不一致，关闭掉这个Socket
                                    MsgNormal ackM=MessageUtils.buildAckMsg(_ms);
                                    ackM.setBizType(15);
                                    ackM.setReturnType(0);//失败
                                    ackM.setSendTime(System.currentTimeMillis());
                                    try { _sendMsgQueue.add(ackM.toBytes()); } catch(Exception e) {}
                                    SocketHandler.this.stopServer();
                                }
                            } else {//从客户端来的消息
                                //绑定客户端
                                bindDeviceFlag=globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, false);
                                if (!bindDeviceFlag) { //与原来记录的不一致，则删除原来的，对于客户端，后到的占用。
                                    SocketHandler _sh=globalMem.getSocketByDevice(_pUdk);
                                    synchronized(_sh.stopLck) {
                                        _sh.stopServer();
                                        if (_sh.getRUN_STATUS()<4&&_sh.canRunning==true) {
                                            try {
                                                _sh.stopLck.wait();
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                    globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, true);
                                }
                                _deviceKey=_pUdk.getDeviceId()+"::"+_pUdk.getPCDType();

                                Map<String, Object> retM=sessionService.dealUDkeyEntry(_pUdk, "socket/entry");
                                if (!(""+retM.get("ReturnType")).equals("1001")) {
                                    MsgNormal ackM=MessageUtils.buildAckMsg(_ms);
                                    ackM.setBizType(15);
                                    ackM.setReturnType(0);//失败
                                    ackM.setSendTime(System.currentTimeMillis());
                                    try { _sendMsgQueue.add(ackM.toBytes()); } catch(Exception e) {}
                                } else {//登录成功
                                    _pUdk.setUserId(""+retM.get("UserId"));
                                    _pushUserKey=_pUdk;

                                    MsgNormal ackM=MessageUtils.buildAckMsg(_ms);
                                    ackM.setBizType(15);
                                    ackM.setReturnType(1);//成功
                                    ackM.setSendTime(System.currentTimeMillis());
                                    try { _sendMsgQueue.add(ackM.toBytes()); } catch(Exception e) { }

                                    //判断剔出
                                    SocketHandler _oldSh=globalMem.getSocketByUser(_pUdk); //和该用户对应的旧的Socket处理
                                    PushUserUDKey _oldUk=(_oldSh==null?null:_oldSh.getPuUDKey()); //旧Socket处理所绑定的UserKey
                                    if (_oldSh!=null&&_oldUk!=null&&!_oldSh.equals(SocketHandler.this)  //1-旧Socket处理不为空；2-旧Socket处理中绑定用户Key不为空；3-新旧Socket处理不相等
                                      &&_oldUk.getPCDType()==_pUdk.getPCDType() //新旧Socket对应设备类型相同
                                      &&_oldUk.getUserId().equals(_pUdk.getUserId()) //新旧Socket对应用户相同
                                      &&!_oldUk.getDeviceId().equals(_pUdk.getDeviceId()) //新旧Socket对应设备号不同
                                    )
                                    //剔出
                                    {
                                        globalMem.kickOut(_pUdk, _oldSh);
                                        MsgNormal msg=new MsgNormal();
                                        msg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                                        msg.setMsgType(0);
                                        msg.setAffirm(0);
                                        msg.setFromType(1);
                                        msg.setToType(0);
                                        msg.setBizType(0x04);
                                        msg.setCmdType(3);
                                        msg.setCommand(1);
                                        msg.setSendTime(System.currentTimeMillis());
                                        Map<String, Object> dataMap=new HashMap<String, Object>();
                                        dataMap.put("UserId", _pUdk.getUserId());
                                        dataMap.put("PCDType", _pUdk.getPCDType());
                                        dataMap.put("DeviceId", _pUdk.getDeviceId());
                                        MapContent mc=new MapContent(dataMap);
                                        msg.setMsgContent(mc);
                                        List<Message> ls=new ArrayList<Message>();
                                        ls.add(msg);
                                        _oldSh.addMessages(ls);
                                    }
                                    globalMem.bindPushUserANDSocket(_pUdk, SocketHandler.this);//绑定信息

                                    //发送注册成功的消息给组对讲和电话——以便他处理组在线的功能
                                    _ms.setAffirm(0);//设置为不需要回复
                                    _ms.setBizType(1);//设置为组消息
                                    _ms.setCmdType(3);//组通知
                                    _ms.setCommand(0);//进入消息
                                    _ms.setUserId(_pUdk.getUserId());
                                    globalMem.receiveMem.addPureMsg(_ms);//对讲组
                                    MsgNormal msc=MessageUtils.clone(_ms);
                                    msc.setMsgId(_ms.getMsgId());
                                    msc.setBizType(2);
                                    globalMem.receiveMem.addPureMsg(msc);//电话
                                }
                            }
                        }
                    }
                } else {//数据流
                    if (_pushUserKey!=null) {
                        ((MsgMedia)_msg).setExtInfo(_pushUserKey);
                        globalMem.receiveMem.addTypeMsg("media", _msg);
                    }
                }
            }
        }
    }
    //接收线程类
    class _ReceiveMsg extends _LoopThread {
        private FileOutputStream fos=null;
        private int _headLen=36;

        private byte[] endMsgFlag={0x00,0x00,0x00};
        private byte[] ba=new byte[20480];//一条消息的内容缓存——最大为2K

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
                                if (_dataLen<0) _dataLen=91;
                                if (i==48&&endMsgFlag[2]==0) _dataLen=80;
                            } else { //非注册消息
                                if (_dataLen<0) _dataLen=45;
                            }
                            if (_dataLen>=0&&i==_dataLen) break;
                        } else  if (isAck==0) {//是一般消息
                            if (isRegist==1) {//是注册消息
                                if (((ba[2]&0x80)==0x80)&&((ba[2]&0x00)==0x00)) {
                                    if (_dataLen<0) _dataLen=91;
                                    if (i==48&&endMsgFlag[2]==0) _dataLen=80;
                                } else {
                                    if (_dataLen<0) _dataLen=90;
                                    if (i==47&&endMsgFlag[2]==0) _dataLen=79;
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
                _sendMsgQueue.add(rB);
            } else { //处理正常消息
                try {
                    Message ms=null;
                    try {
                        ms=MessageUtils.buildMsgByBytes(mba);
                        _recvMsgQueue.add(ms);
                    } catch (Exception e) {
                    }
                    logger.debug("收到消息::"+JsonUtils.objToJson(ms));
                    if (fos!=null) {
                        byte[] aa=JsonUtils.objToJson(ms).getBytes();
                        fos.write(aa, 0, aa.length);
                        fos.write(13);
                        fos.write(10);
                        fos.flush();
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
//            synchronized(_sendMsgQueue) {
//            }
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
        @Override
        protected void __close() {
            //把所有消息发完，才结束
//            synchronized(_sendMsgQueue) {
//            }
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
            try { fos.close(); } catch (Exception e) {} finally{ fos=null; }
        }
    }
    //获取发送消息类
    class _FatchMsg extends _LoopThread {
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
                //获得**具体给这个设备**的消息
                Message m=globalMem.sendMem.getDeviceMsg(_pushUserKey, SocketHandler.this);
                if (m!=null) {
                    if (fos!=null) {//记日志
                        try {
                            byte[] aa=JsonUtils.objToJson(m).getBytes();
                            fos.write(aa, 0, aa.length);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        } catch (IOException e) {
                        }
                    }
                    try {//传消息
                        _sendMsgQueue.add(m.toBytes());
                        //若需要控制确认，插入已发送列表
                        if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(_pushUserKey, m);
                    } catch(Exception e) {
                    }
                }
                //获得**给此用户的通知消息**（与设备无关）
                ConcurrentLinkedQueue<Map<String, Object>> mmq=globalMem.sendMem.getResendMsg(_pushUserKey, SocketHandler.this);
                while (!mmq.isEmpty()) {
                    Map<String, Object> _m=mmq.poll();
                    if (_m==null||_m.isEmpty()) continue;
                    Message _msg=(Message)_m.get("message");
                    if (_msg==null) continue;
                    if (fos!=null) {
                        try {
                            byte[] aa=JsonUtils.objToJson(m).getBytes();
                            fos.write(aa, 0, aa.length);
                            fos.write(13);
                            fos.write(10);
                            fos.flush();
                        } catch (IOException e) {
                        }
                    }
                    try {
                        _sendMsgQueue.add(_msg.toBytes());
                        //更新信息
                        if (((MsgNormal)m).isCtlAffirm()) globalMem.sendMem.updateSendedNeedCtlAffirmMsg(_pushUserKey, _m);
                    } catch(Exception e) {
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