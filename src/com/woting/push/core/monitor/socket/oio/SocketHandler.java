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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.DateUtils;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.mediaflow.mem.TalkMemory;
import com.woting.audioSNS.mediaflow.monitor.DealMediaMsg;
import com.woting.push.PushConstants;
import com.woting.push.config.MediaConfig;
import com.woting.push.config.PushConfig;
import com.woting.push.core.SocketHandleConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

/**
 * 对某一Socket连接的监控服务
 * @author wanghui
 */
public class SocketHandler {
    private Logger logger=LoggerFactory.getLogger(SocketHandler.class);
    private final Object waitBind=new Object();

    private SocketHandleConfig conf;
    private MediaConfig mConf;
    private PushConfig pConf;
    private Socket _socket;
    private BufferedInputStream _socketIn=null;
    private BufferedOutputStream _socketOut=null;
    protected String _deviceKey=null;//和这个Socket绑定的用户
    private PushUserUDKey _pushUserKey=null;//和这个Socket绑定的用户

    public PushUserUDKey getPuUDKey() {
        return _pushUserKey;
    }

    protected LinkedBlockingQueue<byte[]> _sendMsgQueue=new LinkedBlockingQueue<byte[]>();
    protected LinkedBlockingQueue<Message> _recvMsgQueue=new LinkedBlockingQueue<Message>();

    private SessionService sessionService=null;

    private long lastVisitTime=0l;
    private String closeCause="";//关闭socket的原因
    private String socketDesc=null;

    private _ReceiveMsg receiveMsg;
    private _DealMsg dealMsg;
    private _FatchMsg fatchMsg;
    private _SendMsg sendMsg;
    private Timer moniterTimer;
//    private Timer notifyTimer;

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private IntercomMemory intercomMem=IntercomMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();
    private TalkMemory talkMem=TalkMemory.getInstance();

    public volatile Object stopLck=new Object();
    private volatile boolean isRunning=true; //是否可以运行

    @SuppressWarnings("unchecked")
    protected SocketHandler(SocketHandleConfig conf, MediaConfig mConf, Socket socket) {
        socketDesc="Socket["+socket.getRemoteSocketAddress()+"::"+socket.hashCode()+"]";
        this.conf=conf;
        this.mConf=mConf;
        this.pConf=((CacheEle<PushConfig>)SystemCache.getCache(PushConstants.PUSH_CONF)).getContent();
        _socket=socket;
    }

    public void start() {
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

            //启动监控线程
            moniterTimer=new Timer(socketDesc+"监控主线程", true);
            moniterTimer.scheduleAtFixedRate(new MonitorSocket(), 0, conf.get_MonitorDelay());

//            //启动通知控制线程
//            notifyTimer=new Timer(socketDesc+"监控主线程", true);
//            notifyTimer.scheduleAtFixedRate(new NotifyBeat(), 0, conf.get_MonitorDelay());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public boolean socketOk() {
        return _socket!=null&&_socket.isBound()&&_socket.isConnected()&&!_socket.isClosed();
    }

    public boolean isStoped() {
        return !isRunning;
    }

    /**
     * 加入一组消息到队列的尾部
     * @param msgList 消息列表
     */
    public void putMessages(List<Message> msgList) {
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
    public void destroyHandler() {
        if (moniterTimer==null) return ;
        synchronized(stopLck) {
            try {
                if (StringUtils.isNullOrEmptyOrSpace(closeCause)) closeCause="未知原因";
                logger.debug(socketDesc+"关闭::{}", closeCause);

                //1-停止下级服务
                if (dealMsg!=null) {try {dealMsg.__interrupt();} catch(Exception e) {}}
                if (receiveMsg!=null) {try {receiveMsg.__interrupt();} catch(Exception e) {}}
                if (fatchMsg!=null) {
                    try {
                        fatchMsg.__interrupt();
                        synchronized(SocketHandler.this.waitBind) {
                            SocketHandler.this.waitBind.notifyAll();
                        }
                    } catch(Exception e) {}
                }
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
                    try { Thread.sleep(10); } catch (InterruptedException e) {};
                }

                if (dealMsg!=null) {try {dealMsg.interrupt();} catch(Exception e) {}}
                if (receiveMsg!=null) {try {receiveMsg.interrupt();} catch(Exception e) {}}
                if (fatchMsg!=null) {try {fatchMsg.interrupt();} catch(Exception e) {}}
                if (sendMsg!=null) {try {sendMsg.interrupt();} catch(Exception e) {}}
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
                if (moniterTimer!=null) {
                    moniterTimer.cancel();
                    moniterTimer=null;
                }
                isRunning=false;
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
                while (__isRunning&&!__isInterrupted&&socketOk()&&isRunning) {
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
            try {
                msg=_recvMsgQueue.take();
                if (msg==null) return;
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
                try {
                    msg=_recvMsgQueue.take();
                    if (msg==null) continue;
                    __dealOneMsg(msg);
                } catch (Exception e) {
                    logger.debug(StringUtils.getAllMessage(e));
                }
            }
        }

        private void __dealOneMsg(Message _msg) throws InterruptedException {
            if (_msg!=null&&!_msg.isAck()) {
                if (_msg instanceof MsgNormal) {
                    MsgNormal _ms=(MsgNormal)_msg;
                    PushUserUDKey _pUdk=PushUserUDKey.buildFromMsg(_ms);
                    if (_pUdk!=null) {
                        if (_ms.getBizType()!=15) {
                            if (_pUdk.equals(_pushUserKey)) {
                                if (_ms.isCtlAffirm()) { //处理回复消息
                                    MsgNormal ctlAffirmMsg=MessageUtils.buildAckMsg((MsgNormal)_ms);
                                    ctlAffirmMsg.setUserId(pConf.get_ServerType());
                                    ctlAffirmMsg.setDeviceId(pConf.get_ServerName());
                                    try { _sendMsgQueue.put(ctlAffirmMsg.toBytes()); } catch(Exception e) {}
                                }
                                globalMem.receiveMem.putTypeMsg(""+((MsgNormal)_ms).getBizType(), _ms);
                            } else {
                                if (_ms.isCtlAffirm()||_ms.isBizAffirm()) { //处理回复消息
                                    MsgNormal noLogMsg=MessageUtils.buildAckMsg((MsgNormal)_ms);
                                    noLogMsg.setCmdType(1);
                                    noLogMsg.setUserId(pConf.get_ServerType());
                                    noLogMsg.setDeviceId(pConf.get_ServerName());
                                    try { _sendMsgQueue.put(noLogMsg.toBytes()); } catch(Exception e) {}
                                }
                            }
                        } else {//是注册消息
                            boolean bindDeviceFlag=false;
                            if (_ms.getFromType()==0) {//从服务器来的消息，对于服务器，先到的占用。
                                _pushUserKey=_pUdk;
                                synchronized(SocketHandler.this.waitBind) {
                                    SocketHandler.this.waitBind.notifyAll();
                                }
                                bindDeviceFlag=globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, false);
                                if (bindDeviceFlag) {
                                    globalMem.bindPushUserANDSocket(_pUdk, SocketHandler.this);//处理注册
                                    _deviceKey=_pUdk.getDeviceId()+"::"+_pUdk.getPCDType();
                                } else {//与之前的注册不一致，关闭掉这个Socket
                                    MsgNormal ackM=MessageUtils.buildAckEntryMsg(_ms);
                                    ackM.setReturnType(0);//失败
                                    ackM.setSendTime(System.currentTimeMillis());
                                    try { _sendMsgQueue.put(ackM.toBytes()); } catch(Exception e) {}
                                    SocketHandler.this.destroyHandler();
                                }
                            } else {//从客户端来的消息
                                //绑定客户端
                                bindDeviceFlag=globalMem.bindDeviceANDsocket(_pUdk, SocketHandler.this, false);
                                if (!bindDeviceFlag) { //与原来记录的不一致，则删除原来的，对于客户端，后到的占用。
                                    SocketHandler _sh=globalMem.getSocketByDevice(_pUdk);
                                    synchronized(_sh.stopLck) {
                                        _sh.destroyHandler();
                                        if (_sh.isRunning) {
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
                                    MsgNormal ackM=MessageUtils.buildAckEntryMsg(_ms);
                                    ackM.setReturnType(0);//失败
                                    ackM.setSendTime(System.currentTimeMillis());
                                    try { _sendMsgQueue.put(ackM.toBytes()); } catch(Exception e) {}
                                } else {//登录成功
                                    _pUdk.setUserId(""+retM.get("UserId"));
                                    _pushUserKey=_pUdk;
                                    synchronized(SocketHandler.this.waitBind) {
                                        SocketHandler.this.waitBind.notifyAll();
                                    }
                                    MsgNormal ackM=MessageUtils.buildAckEntryMsg(_ms);
                                    ackM.setReturnType(1);//成功
                                    ackM.setUserId(pConf.get_ServerType());
                                    ackM.setDeviceId(pConf.get_ServerName());
                                    ackM.setSendTime(System.currentTimeMillis());
                                    try { _sendMsgQueue.put(ackM.toBytes()); } catch(Exception e) { }

                                    //判断踢出
                                    SocketHandler _oldSh=globalMem.getSocketByUser(_pUdk); //和该用户对应的旧的Socket处理
                                    PushUserUDKey _oldUk=(_oldSh==null?null:_oldSh.getPuUDKey()); //旧Socket处理所绑定的UserKey
                                    boolean _oldNeedKickOut=sessionService.needKickOut(_oldUk);
                                    if (_oldSh!=null&&_oldUk!=null&&!_oldSh.equals(SocketHandler.this)  //1-旧Socket处理不为空；2-旧Socket处理中绑定用户Key不为空；3-新旧Socket处理不相等
                                      &&_oldUk.getPCDType()==_pUdk.getPCDType() //新旧Socket对应设备类型相同
                                      &&_oldUk.getUserId().equals(_pUdk.getUserId()) //新旧Socket对应用户相同
                                      &&!_oldUk.getDeviceId().equals(_pUdk.getDeviceId())
                                      &&_oldNeedKickOut //旧账号在登录状态
                                    )
                                    {//踢出
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
                                        _oldSh.putMessages(ls);
                                    }
                                    globalMem.bindPushUserANDSocket(_pUdk, SocketHandler.this);//绑定信息

                                    //发送注册成功的消息给组对讲和电话——以便他处理组在线的功能
                                    //对讲组
                                    _ms.setAffirm(0);//设置为不需要回复
                                    _ms.setBizType(1);//设置为组消息
                                    _ms.setCmdType(3);//组通知
                                    _ms.setCommand(0);
                                    _ms.setUserId(_pUdk.getUserId());
                                    globalMem.receiveMem.putTypeMsg(""+((MsgNormal)_ms).getBizType(), _ms);
                                    //电话
                                    MsgNormal msc=MessageUtils.clone(_ms);
                                    msc.setMsgId(_ms.getMsgId());
                                    msc.setBizType(2);
                                    globalMem.receiveMem.putTypeMsg(""+msc.getBizType(), msc);
                                }
                            }
                        }
                    }
                } else { //数据流
                    boolean isBind=false;
                    if (_pushUserKey!=null) {
                        isBind=true;
                        ((MsgMedia)_msg).setExtInfo(_pushUserKey);
                        //globalMem.receiveMem.putTypeMsg("media", _msg);
                        DealMediaMsg dmm=new DealMediaMsg((((MsgMedia)_msg).getMediaType()==1?"音频":"视频")
                            +(((MsgMedia)_msg).isAck()?"回执":"数据")+"包处理：[通话类型="+(((MsgMedia)_msg).getBizType()==1?"对讲":"电话")
                            +"；talkId="+((MsgMedia)_msg).getTalkId()+"；seqNo="+((MsgMedia)_msg).getSeqNo()+"；]"
                            ,(MsgMedia)_msg, globalMem, intercomMem, callingMem, talkMem, sessionService);
                        dmm.start();
                    }
                    if (((MsgMedia)_msg).isCtlAffirm()&&!isBind) {
                        MsgMedia retMm=new MsgMedia();
                        retMm.setFromType(1);
                        retMm.setToType(0);
                        retMm.setMsgType(0);
                        retMm.setAffirm(0);
                        retMm.setBizType(((MsgMedia)_msg).getBizType());
                        retMm.setTalkId(((MsgMedia)_msg).getTalkId());
                        retMm.setChannelId(((MsgMedia)_msg).getChannelId());
                        retMm.setSeqNo(((MsgMedia)_msg).getSeqNo());
                        retMm.setSendTime(System.currentTimeMillis());
                        retMm.setReturnType(0);//未绑定用户，不能处理这样的媒体包
                        try { _sendMsgQueue.put(retMm.toBytes()); } catch(Exception e) { }
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

            int msgType=-1, r=-1, i=0, isAck=-1, isRegist=0, isCtlAck=0, tempFlag=0, fieldFlag=0, countFlag=0;
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
                            tempFlag=i;
                            if ((ba[2]&0x80)==0x80) isAck=1; else isAck=0;
                            if (isAck==1) countFlag=1; else countFlag=0;

                            if (((ba[i-1]>>4)&0x0F)==0x0F) isRegist=1;
                            else
                            if (((ba[i-1]>>4)|0x00)==0x00) isCtlAck=1;
                        }
                        if (isAck!=-1) {
                            if (isCtlAck==1) {
                                if (fieldFlag==0&&((endMsgFlag[1]=='|'&&endMsgFlag[2]=='|')||(i-tempFlag-countFlag)==32)) {
                                    countFlag=1;
                                    tempFlag=i;
                                    fieldFlag=1;
                                } else
                                if (fieldFlag==1&&(i-tempFlag)>countFlag&&(ba[i-countFlag]==0||(endMsgFlag[1]=='|'&&endMsgFlag[2]=='|')||(i-tempFlag-countFlag)==12)) {
                                    countFlag=0;
                                    tempFlag=i;
                                    fieldFlag=2;
                                } else
                                if (fieldFlag==2&&((endMsgFlag[1]=='|'&&endMsgFlag[2]=='|')||(i-tempFlag-countFlag)==32)) {
                                    break;//通用回复消息读取完毕
                                }
                            } else if (isRegist==1) {
                                if (fieldFlag==0&&(i-tempFlag)>countFlag&&((endMsgFlag[1]=='|'&&endMsgFlag[2]=='|')||(i-tempFlag-countFlag)==32)) {
                                    countFlag=1;
                                    tempFlag=i;
                                    fieldFlag=1;
                                } else
                                if (fieldFlag==1&&(i-tempFlag)>countFlag&&((ba[i-countFlag]==0||(endMsgFlag[1]=='|'&&endMsgFlag[2]=='|')||(i-tempFlag-countFlag)==12))) {
                                    countFlag=0;
                                    tempFlag=i;
                                    fieldFlag=2;
                                } else
                                if (fieldFlag==2&&((endMsgFlag[1]=='|'&&endMsgFlag[2]=='|')||(i-tempFlag-countFlag)==32)) {
                                    break;//注册消息完成
                                }
                            } else { //一般消息
                                if (fieldFlag==0&&endMsgFlag[1]=='^'&&endMsgFlag[2]=='^') {
                                    fieldFlag=1;
                                    tempFlag=0;
                                } else
                                if (fieldFlag==1&&(++tempFlag)==2) {
                                    _dataLen=(short)(((endMsgFlag[2]<<8)|endMsgFlag[1]&0xff));
                                    tempFlag=0;
                                    fieldFlag=2;
                                } else
                                if (fieldFlag==2&&(++tempFlag)==_dataLen) break;
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

            if (i<3) {//出现了问题，让Cpu休息一下
                sleep(500);
                return;
            }
            if (fos!=null) {
                String timeStr="["+System.currentTimeMillis()+"]";
                fos.write(timeStr.getBytes(), 0, timeStr.getBytes().length);
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
                //_sendMsgQueue.put(rB);
            } else { //处理正常消息
                try {
                    Message ms=null;
                    try {
                        ms=MessageUtils.buildMsgByBytes(mba);
                        logger.debug("收到消息::"+JsonUtils.objToJson(ms));
                        _recvMsgQueue.put(ms);
                    } catch (Exception e) {
                    }
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
            mBytes=_sendMsgQueue.take();
            if (mBytes==null||mBytes.length<=2) return;
            if (_socketOut!=null&&!_socket.isOutputShutdown()) {
                _socketOut.write(mBytes);
                _socketOut.flush();
                if (fos!=null) {
                    try {
                        fos.write(mBytes);
                        String timeStr="["+System.currentTimeMillis()+"]";
                        fos.write(timeStr.getBytes(), 0, timeStr.getBytes().length);
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
                try {
                    mBytes=_sendMsgQueue.take();
                    if (mBytes==null||mBytes.length<=2) continue;
                    _socketOut.write(mBytes);
                    _socketOut.flush();
                } catch (Exception e) {
                    logger.debug(StringUtils.getAllMessage(e));
                }
                if (fos!=null) {
                    try {
                        fos.write(mBytes);
                        fos.write(13);
                        fos.write(10);
                        fos.flush();
                    } catch (IOException e) {}
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
            //等待绑定后再开始Fatch
            synchronized(SocketHandler.this.waitBind) {
                if (SocketHandler.this._pushUserKey==null) {
                    try {
                        SocketHandler.this.waitBind.wait();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (_pushUserKey!=null) {
                //获得**具体给这个设备**的消息——控制消息
                synchronized(_pushUserKey) {
                    Message m=globalMem.sendMem.pollDeviceMsgCTL(_pushUserKey, SocketHandler.this);
                    if (m!=null) {
                        if (fos!=null) {//记日志
                            try {
                                byte[] aa=JsonUtils.objToJson(m).getBytes();
                                fos.write(aa, 0, aa.length);
                                fos.write(13);
                                fos.write(10);
                                fos.flush();
                            } catch (IOException e) {}
                        }
                        try {//传消息
                            if (m instanceof MsgNormal) {
                                MsgNormal mn=(MsgNormal)m;
                                if (mn.getFromType()==0) {
                                    mn.setUserId(pConf.get_ServerType());
                                    mn.setDeviceId(pConf.get_ServerName());
                                }
                                _sendMsgQueue.put(m.toBytes());
                                //若需要控制确认，插入已发送列表
                                if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(_pushUserKey, m);
                            }
                        } catch(Exception e) {}
                    }
                    //获得**具体给这个设备**的消息——媒体消息
                    m=globalMem.sendMem.pollDeviceMsgMDA(_pushUserKey, SocketHandler.this);
                    if (m!=null) {
                        if (System.currentTimeMillis()-((MsgMedia)m).getSendTime()<(((MsgMedia)m).getMediaType()==1?mConf.get_AudioExpiredTime():mConf.get_VedioExpiredTime())) {
                            //若超出媒体包延迟时间，就不发这个媒体包了
                            if (fos!=null) {//记日志
                                try {
                                    byte[] aa=JsonUtils.objToJson(m).getBytes();
                                    fos.write(aa, 0, aa.length);
                                    fos.write(13);
                                    fos.write(10);
                                    fos.flush();
                                } catch (IOException e) {}
                            }
                            //传消息
                            try { _sendMsgQueue.put(m.toBytes()); } catch(Exception e) {}
                        }
                   }
                    //获得**给此用户的通知消息**（与设备无关）
                    Message nm=globalMem.sendMem.pollNotifyMsg(_pushUserKey, SocketHandler.this);
                    if (nm!=null&&!(nm instanceof MsgMedia)) {
                        if (fos!=null) {
                            try {
                                byte[] aa=JsonUtils.objToJson(m).getBytes();
                                fos.write(aa, 0, aa.length);
                                fos.write(13);
                                fos.write(10);
                                fos.flush();
                            } catch (IOException e) {}
                        }
                        try {
                            if (m instanceof MsgNormal) {
                                MsgNormal mn=(MsgNormal)m;
                                if (mn.getFromType()==0) {
                                    mn.setUserId(pConf.get_ServerType());
                                    mn.setDeviceId(pConf.get_ServerName());
                                }
                                _sendMsgQueue.put(m.toBytes());
                                //若需要控制确认，插入已发送列表
                                if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(_pushUserKey, m);
                            }
                        } catch(Exception e) {}
                    }
                    //获得**需要重复发送的消息**
                    LinkedBlockingQueue<Map<String, Object>> mmq=globalMem.sendMem.getResendMsg(_pushUserKey, SocketHandler.this);
                    while (mmq!=null&&!mmq.isEmpty()) {
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
                            } catch (IOException e) {}
                        }
                        try {
                            if (m instanceof MsgNormal) {
                                MsgNormal mn=(MsgNormal)m;
                                if (mn.getFromType()==0) {
                                    mn.setUserId(pConf.get_ServerType());
                                    mn.setDeviceId(pConf.get_ServerName());
                                }
                                _sendMsgQueue.put(m.toBytes());
                                //若需要控制确认，插入已发送列表
                                if (m.isCtlAffirm()) globalMem.sendMem.addSendedNeedCtlAffirmMsg(_pushUserKey, m);
                            }
                        } catch(Exception e) {}
                    }
                    try { _pushUserKey.wait(20); } catch (InterruptedException ie) {};
                }
            }
        }
        @Override
        protected void __close() {
            try { fos.close(); } catch (Exception e) {} finally{ fos=null; }
        }
    }

    class MonitorSocket extends TimerTask {
        public void run() {
            boolean canContinue=true;
            long __period=System.currentTimeMillis()-lastVisitTime;
            if (__period>conf.get_ExpireTime()) {
                long t=System.currentTimeMillis();
                if (StringUtils.isNullOrEmptyOrSpace(closeCause)) {
                    closeCause="超时关闭:最后访问时间["
                        +DateUtils.convert2LocalStr("yyyy-MM-dd HH:mm:ss:SSS", new Date(lastVisitTime))+"("+lastVisitTime+")],当前时间["
                        +DateUtils.convert2LocalStr("yyyy-MM-dd HH:mm:ss:SSS", new Date(t))+"("+t+")],过期时间["
                        +conf.get_ExpireTime()+"]，超时时长["+__period+"]";
                }
                canContinue=false;
            } else canContinue=socketOk()&&isRunning;
            if (dealMsg!=null) canContinue=canContinue?(!dealMsg.__isInterrupted&&dealMsg.__isRunning):canContinue;
            if (receiveMsg!=null) canContinue=canContinue?(!receiveMsg.__isInterrupted&&receiveMsg.__isRunning):canContinue;
            if (fatchMsg!=null) canContinue=canContinue?(!fatchMsg.__isInterrupted&&fatchMsg.__isRunning):canContinue;
            if (sendMsg!=null) canContinue=canContinue?(!sendMsg.__isInterrupted&&sendMsg.__isRunning):canContinue;
            if (!canContinue) destroyHandler();
        }
    }
}