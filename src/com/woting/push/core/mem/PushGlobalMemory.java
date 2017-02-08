package com.woting.push.core.mem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.GroupPo;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.passport.UGA.service.GroupService;
import com.woting.passport.UGA.service.UserService;
import com.woting.push.PushConstants;
import com.woting.push.config.AffirmCtlConfig;
import com.woting.push.config.MediaConfig;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.socket.oio.SocketHandler;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

/**
 * 处理全局内存
 * <pre>
 * 处理消息的全局内存，包括：
 * 1-收到消息的队列
 * 2-发送消息的队列
 * </pre>
 * @author wanghui
 */
public class PushGlobalMemory {
    private SessionService sessionService=null;
    private GroupService groupService=null;
    private UserService userService=null;

    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static PushGlobalMemory instance=new PushGlobalMemory();
    }
    /**
     * 得到单例的对象
     * @return 接收消息对象
     */
    public static PushGlobalMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    /*
     * 初始化，创建两个主要的对象
     */
    private PushGlobalMemory() {
        sessionService=(SessionService)SpringShell.getBean("sessionService");
        groupService=(GroupService)SpringShell.getBean("groupService");
        userService=(UserService)SpringShell.getBean("userService");

        acc=(AffirmCtlConfig)SystemCache.getCache(PushConstants.AFFCTL_CONF).getContent();
        mc=(MediaConfig)SystemCache.getCache(PushConstants.MEDIA_CONF).getContent();

        groupMap=new ConcurrentHashMap<String, Group>();
        userMap=new ConcurrentHashMap<String, UserPo>();

        //初始化分类消息队列
        recvTypeMsg=new ConcurrentHashMap<String, LinkedBlockingQueue<Message>>();
        LinkedBlockingQueue<Message> typeDeque1=new LinkedBlockingQueue<Message>();
        recvTypeMsg.put("1", typeDeque1);
        LinkedBlockingQueue<Message> typeDeque2=new LinkedBlockingQueue<Message>();
        recvTypeMsg.put("2", typeDeque2);
        LinkedBlockingQueue<Message> typeDeque4=new LinkedBlockingQueue<Message>();
        recvTypeMsg.put("4", typeDeque4);
        LinkedBlockingQueue<Message> typeDeque8=new LinkedBlockingQueue<Message>();
        recvTypeMsg.put("8", typeDeque8);

        //LCKMAP_pkuANDhadmsgtobesend=new ConcurrentHashMap<PushUserUDKey, Object>();
        send2DeviceMsgCTL=new ConcurrentHashMap<PushUserUDKey, LinkedBlockingQueue<Message>>();
        send2DeviceMsgMDA=new ConcurrentHashMap<PushUserUDKey, LinkedBlockingQueue<Message>>();
        sendedNeedCtlAffirmMsg=new ConcurrentHashMap<PushUserUDKey, LinkedBlockingQueue<Map<String, Object>>>();

        notifyMsg=new ConcurrentHashMap<String, LinkedBlockingQueue<Message>>();

        REF_deviceANDsocket=new HashMap<String, SocketHandler>();
        REF_userdtypeANDsocket=new HashMap<String, SocketHandler>();
        REF_userdtypeANDudk=new HashMap<String, PushUserUDKey>();
        REF_udkANDsocket=new HashMap<PushUserUDKey, SocketHandler>();
        REF_socketANDudk=new HashMap<SocketHandler, PushUserUDKey>();

        receiveMem=new ReceiveMemory();
        sendMem=new SendMemory();
        uANDgMem=new UserAndGroupMemory();

        uANDgMem.loadFromDB();
    }

    //==========配置信息
    private AffirmCtlConfig acc;
    private MediaConfig mc;

    //==========用户用户组内存
    /**
     * 组信息缓存，目前缓存所有组信息
     */
    private ConcurrentHashMap<String, Group> groupMap;
    /**
     * 组信息缓存，目前缓存所有组信息
     */
    private ConcurrentHashMap<String, UserPo> userMap;

    //==========接收消息内存
    /**
     * 分类接收队列，不同类型的消息会由不同类去处理<br/>
     * 注意，这里不包括媒体流数据
     * <pre>
     * Key    分类的描述
     * Value  是要处理的队列
     * </pre>
     */
    private ConcurrentHashMap<String, LinkedBlockingQueue<Message>> recvTypeMsg;

    //==========发送消息内存
    /**
     * 是否有消息需要发送的锁，不包括通知类消息
     */
    //private Map<PushUserUDKey, Object> LCKMAP_pkuANDhadmsgtobesend=null;
    /**
     * 发送消息队列——发送给具体的设备(控制消息)
     * <pre>
     * Key    发送消息的目标，用户的Key
     * Value  消息队列
     * </pre>
     */
    private ConcurrentHashMap<PushUserUDKey, LinkedBlockingQueue<Message>> send2DeviceMsgCTL;
    /**
     * 发送消息队列——发送给具体的设备(媒体数据消息)
     * <pre>
     * Key    发送消息的目标，用户的Key
     * Value  消息队列
     * </pre>
     */
    private ConcurrentHashMap<PushUserUDKey, LinkedBlockingQueue<Message>> send2DeviceMsgMDA;
    /**
     * 已发送的需要控制回复的消息，此类消息与设备号绑定
     * <pre>
     * Key    用户Id
     * Value  消息Map:FirstSendTime,Message
     * </pre>
     */
    private ConcurrentHashMap<PushUserUDKey, LinkedBlockingQueue<Map<String, Object>>> sendedNeedCtlAffirmMsg;

    //通知消息队列
    /**
     * 给用户发送的通知消息队列——发送的通知消息
     * <pre>
     * Key    用户Id
     * Value  消息队列
     * </pre>
     */
    private ConcurrentHashMap<String, LinkedBlockingQueue<Message>> notifyMsg;

    //    private Object LOCK_unionKey=new Object(); //同一Key锁
    private Map<String, SocketHandler> REF_deviceANDsocket;   //设备和Socket处理线程的对应表； Key——设备标识：DeviceId::PCDType；Value——Socket处理线程
    private Map<String, SocketHandler> REF_userdtypeANDsocket;//用户设备和Socket处理线程的对应表； Key——设备标识：UserId::PCDType；Value——Socket处理线程
    private Map<String, PushUserUDKey> REF_userdtypeANDudk;   //用户设备和Key的对应表； Key——设备标识：UserId::PCDType；Value——PushUserUDKey
    private Map<PushUserUDKey, SocketHandler> REF_udkANDsocket;
    private Map<SocketHandler, PushUserUDKey> REF_socketANDudk;

    /**
     * 绑定用户和Socket
     * @param pUk 用户key
     * @param sh  SocketHandler处理线程
     */
    public void registSocketHandler(SocketHandler sh) {
        if (sh==null) return;
//        synchronized(LOCK_unionKey) {
//        }
        REF_socketANDudk.put(sh, null);
    }
    /**
     * 绑定设备和Socket
     * @param pUk 用户key
     * @param sh SocketHandler处理线程
     * @param force 是否强制绑定
     * @return 若绑定成功返回true，否则返回false(若所给sh与系统记录的不一致，则不进行绑定，返回false，除非force==true)
     */
    public boolean bindDeviceANDsocket(PushUserUDKey pUdk, SocketHandler sh, boolean force) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null||sh==null) return false;
        if (force) {
            REF_deviceANDsocket.put(pUdk.getDeviceId()+"::"+pUdk.getPCDType(), sh);
            //LCKMAP_pkuANDhadmsgtobesend.put(pUdk, new Object());
            return true;
        } else {
            SocketHandler _sh=REF_deviceANDsocket.get(pUdk.getDeviceId()+"::"+pUdk.getPCDType());
            if (_sh!=null) {
                if (!sh.equals(_sh)) return false;
                return true;
            } else {
                REF_deviceANDsocket.put(pUdk.getDeviceId()+"::"+pUdk.getPCDType(), sh);
                //LCKMAP_pkuANDhadmsgtobesend.put(pUdk, new Object());
                return true;
            }
        }
    }
    /**
     * 解除设备和Socket的绑定，只在Socket停止时使用
     * @param pUk 用户key
     * @param sh SocketHandler处理线程
     * @param 若给定的sh和系统中记录的sh不一致，则不进行删除
     */
    public boolean unbindDeviceANDsocket(PushUserUDKey pUdk, SocketHandler sh) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null||sh==null) return true;
        SocketHandler _sh=REF_deviceANDsocket.get(pUdk.getDeviceId()+"::"+pUdk.getPCDType());
        if (_sh==null) return true;
        if (!_sh.equals(sh)) return false;
        REF_deviceANDsocket.remove(pUdk.getDeviceId()+"::"+pUdk.getPCDType());
        //LCKMAP_pkuANDhadmsgtobesend.remove(pUdk);
        return true;
    }
    /**
     * 获得设备和Socket的绑定关系
     * @param pUk 设备Key
     */
    public SocketHandler getSocketByDevice(PushUserUDKey pUdk) {
//      synchronized(LOCK_unionKey) {
//      }
        return REF_deviceANDsocket.get(pUdk.getDeviceId()+"::"+pUdk.getPCDType());
    }

    /**
     * 绑定用户和Socket
     * @param pUk 用户key
     * @param sh SocketHandler处理线程
     */
    public void bindPushUserANDSocket(PushUserUDKey pUdk, SocketHandler sh) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null||sh==null) return;
//        synchronized(LOCK_usersocketMap) {
//        }

        //        SocketHandler oldSh=REF_udkANDsocket.get(pUdk);
//        if (oldSh!=null) {
//            if (!oldSh.equals(sh)) {
//                synchronized(oldSh.stopLck) {
//                    List<Message> ml=new ArrayList<Message>();
//                    ml.add(buildKickOutMsg(pUdk));
//                    
//                    oldSh.stopServer(ml);
//                    try {
//                        oldSh.stopLck.wait();
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//
//        //剔除相同用户同一设备登录
//        PushUserUDKey _pUdk=REF_userdtypeANDudk.get(pUdk.getUserId()+"::"+pUdk.getPCDType());
//        if (_pUdk!=null&&!_pUdk.equals(pUdk)) {
//            oldSh=REF_udkANDsocket.get(_pUdk);
//            if (oldSh!=null) {
//                if (oldSh.equals(sh)) {
//                    synchronized(oldSh.stopLck) {
//                        List<Message> ml=new ArrayList<Message>();
//                        ml.add(buildKickOutMsg(_pUdk));
//                        oldSh.stopServer(ml);
//                        try {
//                            oldSh.stopLck.wait();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            }
//        }
        REF_udkANDsocket.put(pUdk, sh);
        REF_socketANDudk.put(sh, pUdk);
        if (!StringUtils.isNullOrEmptyOrSpace(pUdk.getUserId())) {
            REF_userdtypeANDudk.put(pUdk.getUserId()+"::"+pUdk.getPCDType(), pUdk);
            REF_userdtypeANDsocket.put(pUdk.getUserId()+"::"+pUdk.getPCDType(), sh);
        }
    }
    /**
     * 剔出用户
     * @param pUdk 新用户Key
     * @param sh 旧用户对应的Socket处理
     */
    public boolean kickOut(PushUserUDKey pUdk, SocketHandler oldSh) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null||oldSh==null) return false;
        if (oldSh.getPuUDKey()==null) return false;
        PushUserUDKey oldUdk=oldSh.getPuUDKey();
        if (!oldUdk.getUserId().equals(pUdk.getUserId())||oldUdk.getPCDType()!=pUdk.getPCDType()) return false;

        PushUserUDKey _oldUdk=REF_socketANDudk.get(oldSh);
        REF_udkANDsocket.remove(oldUdk);
        if (!_oldUdk.equals(oldUdk)) REF_socketANDudk.put(oldSh, null);
        return true;
    }
    /**
     * 删除用户和Socket处理之间的绑定
     * @param pUk 用户key，可为空
     * @param sh  SocketHandler处理线程，可为空
     */
    public void unbindPushUserANDSocket(PushUserUDKey pUdk, SocketHandler sh) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null&&sh==null) return;
        PushUserUDKey _pUdk=null;
        SocketHandler _sh=null;

        if (pUdk==null) {
            _pUdk=REF_socketANDudk.get(sh);
            if (_pUdk!=null) {
                REF_udkANDsocket.remove(_pUdk);
                if (!StringUtils.isNullOrEmptyOrSpace(_pUdk.getUserId())) {
                    PushUserUDKey _pUdk2=REF_userdtypeANDudk.get(_pUdk.getUserId()+"::"+_pUdk.getPCDType());
                    if (_pUdk2!=null&&_pUdk2.equals(_pUdk)) {
                        REF_userdtypeANDudk.remove(_pUdk.getUserId()+"::"+_pUdk.getPCDType());
                    }
                    _sh=REF_userdtypeANDsocket.get(_pUdk.getUserId()+"::"+_pUdk.getPCDType());
                    if (_sh!=null&&_sh.equals(sh)) {
                        REF_userdtypeANDsocket.remove(_pUdk.getUserId()+"::"+_pUdk.getPCDType());
                    }
                }
            }
            REF_socketANDudk.remove(sh);
        } else if (sh==null) {
            REF_udkANDsocket.remove(pUdk);
            _sh=REF_udkANDsocket.get(pUdk);
            if (_sh!=null) REF_socketANDudk.remove(_sh);
            if (!StringUtils.isNullOrEmptyOrSpace(pUdk.getUserId())) {
                _pUdk=REF_userdtypeANDudk.get(pUdk.getUserId()+"::"+pUdk.getPCDType());
                if (_pUdk!=null&&_pUdk.equals(pUdk)) {
                    REF_userdtypeANDudk.remove(pUdk.getUserId()+"::"+pUdk.getPCDType());
                }
                SocketHandler _sh2=REF_userdtypeANDsocket.get(pUdk.getUserId()+"::"+pUdk.getPCDType());
                if (_sh2!=null&&_sh2.equals(_sh)) {
                    REF_userdtypeANDsocket.remove(pUdk.getUserId()+"::"+pUdk.getPCDType());
                }
            }
        } else {
            REF_udkANDsocket.remove(pUdk);
            REF_socketANDudk.remove(sh);
            if (!StringUtils.isNullOrEmptyOrSpace(pUdk.getUserId())) {
                _pUdk=REF_userdtypeANDudk.get(pUdk.getUserId()+"::"+pUdk.getPCDType());
                if (_pUdk!=null&&_pUdk.equals(pUdk)) {
                    REF_userdtypeANDudk.remove(pUdk.getUserId()+"::"+pUdk.getPCDType());
                }
                _sh=REF_userdtypeANDsocket.get(pUdk.getUserId()+"::"+pUdk.getPCDType());
                if (_sh!=null&&_sh.equals(sh)) {
                    REF_userdtypeANDsocket.remove(pUdk.getUserId()+"::"+pUdk.getPCDType());
                }
            }
        }
    }
    public PushUserUDKey getPushUserBySocket(SocketHandler sh) {
//      synchronized(LOCK_unionKey) {
//      }
        if (sh==null) return null;
        return REF_socketANDudk.get(sh);
    }
    public SocketHandler getSocketByPushUser(PushUserUDKey pUdk) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null) return null;
        return REF_udkANDsocket.get(pUdk);
    }
    public SocketHandler getSocketByUser(PushUserUDKey pUdk) {
//      synchronized(LOCK_unionKey) {
//      }
        if (pUdk==null) return null;
        return REF_userdtypeANDsocket.get(pUdk.getUserId()+"::"+pUdk.getPCDType());
    }
    /**
     * 得到注册的仍然活动的Socket处理线程
     * @return 返回活动的Socket处理线程列表
     */
    public List<SocketHandler> getSochekHanders() {
        List<SocketHandler> ret=new ArrayList<SocketHandler>();
        for (SocketHandler sh:REF_socketANDudk.keySet()) {
            ret.add(sh);
        }
        return ret.isEmpty()?null:ret;
    }

//    private Message buildKickOutMsg(PushUserUDKey pUdk) {
//        MsgNormal msg=new MsgNormal();
//        msg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
//        msg.setFromType(1);
//        msg.setToType(0);
//        msg.setMsgType(0);
//        msg.setAffirm(0);
//        msg.setBizType(0x04);
//        msg.setCmdType(3);
//        msg.setCommand(1);
//        Map<String, Object> dataMap=pUdk.toHashMap();
//        MapContent mc=new MapContent(dataMap);
//        msg.setMsgContent(mc);
//
//        return msg;
//    }

    public ReceiveMemory receiveMem=null;
    public SendMemory sendMem=null;
    public UserAndGroupMemory uANDgMem=null;

    /**
     * 内部类，接受消息处理类
     * @author wanghui
     */
    public class ReceiveMemory {
        /**
         * 加入分类处理的消息队列
         * @param type 分类标志
         * @param msg 消息
         * @throws InterruptedException
         */
        public void putTypeMsg(String type, Message msg) throws InterruptedException {
            LinkedBlockingQueue<Message> typeDeque=recvTypeMsg.get(type);
            if (typeDeque!=null) typeDeque.put(msg);
        }

        /**
         * 从分类处理的队列获取消息，并从该队列移除消息
         * @param type 分类标志
         * @return 消息
         * @throws InterruptedException 
         */
        public Message takeTypeMsg(String type) throws InterruptedException {
            //if (typeMsg==null||typeMsg.get(type)==null) return null;
            return recvTypeMsg.get(type).take();
        }
    }

    /**
     * 内部类，发送消息处理类
     * @author wanghui
     */
    public class SendMemory {
//        public Object getSendLock(PushUserUDKey pUDkey) {
//            return LCKMAP_pkuANDhadmsgtobesend.get(pUDkey);
//        }
        //到具体设备的操作
        /*
         * [发送消息队列-直接对应（设备+用户）]
         * 把消息(msg)加入所对应用户设备(pUDkey)的传送队列<br/>
         * 若消息已经存在于传送队列中，就不加入了。
         * @param pUDkey 用户Key
         * @param msg 消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         * @throws InterruptedException 
         */
        public void putDeviceMsg(PushUserUDKey pUDkey, Message msg) throws InterruptedException {
            if (send2DeviceMsgCTL==null||msg==null||pUDkey==null) return;
            LinkedBlockingQueue<Message> _deviceQueueCTL=((msg instanceof MsgNormal)?send2DeviceMsgCTL.get(pUDkey):send2DeviceMsgMDA.get(pUDkey));
            if (_deviceQueueCTL==null) {
                _deviceQueueCTL=new LinkedBlockingQueue<Message>();
                if (msg instanceof MsgNormal) send2DeviceMsgCTL.put(pUDkey, _deviceQueueCTL);
                else send2DeviceMsgMDA.put(pUDkey, _deviceQueueCTL);
            }
            if (msg.getSendTime()==0) msg.setSendTime(System.currentTimeMillis());
            //查找相同的消息是否存在
            boolean _exist=false;
            for (Message _msg: _deviceQueueCTL) {
                if (_msg.equals(msg)) {
                    _exist=true;
                    break;
                }
            }
            if (!_exist) _deviceQueueCTL.put(msg);

//            Object _lck=LCKMAP_pkuANDhadmsgtobesend.get(pUDkey);
//            if (_lck==null) return;
//            synchronized(_lck) {
//                _lck.notifyAll();
//            }
        }

        /**
         * [发送消息队列-直接对应（设备+用户）](控制消息)
         * 获得设备用户的发送消息——控制消息<br/>
         * @param pUDkey 用户Key
         * @param msg 消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         * @throws InterruptedException 
         */
        public Message pollDeviceMsgCTL(PushUserUDKey pUdk, SocketHandler sh) throws InterruptedException {
            if (pUdk==null||sh==null) return null;
            Message m=null;
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                LinkedBlockingQueue<Message> mQueue=send2DeviceMsgCTL.get(pUdk);
                if (mQueue!=null) m=mQueue.poll();
            }
            return m;
        }
        /**
         * [发送消息队列-直接对应（设备+用户）](媒体消息)
         * 获得设备用户的发送消息——媒体消息<br/>
         * @param pUDkey 用户Key
         * @param msg 消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         * @throws InterruptedException 
         */
        public Message pollDeviceMsgMDA(PushUserUDKey pUdk, SocketHandler sh) throws InterruptedException {
            if (pUdk==null||sh==null) return null;
            Message m=null;
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                LinkedBlockingQueue<Message> mQueue=send2DeviceMsgMDA.get(pUdk);
                if (mQueue!=null) m=mQueue.poll();
                //检查是否过期
                if (m!=null) {
                    if (((MsgMedia)m).isAudio()&&(System.currentTimeMillis()-((MsgMedia)m).getSendTime())>mc.get_AudioPackExpiredTime()) m=null;
                    else
                    if (((MsgMedia)m).isVedio()&&(System.currentTimeMillis()-((MsgMedia)m).getSendTime())>mc.get_VedioPackExpiredTime()) m=null;
                }
            }
            return m;
        }

        //通知类消息操作
        /**
         * [发送消息队列-直接对应（用户）](通知消息)
         * 把消息(msg)加入所对应用的通知消息传送队列<br/>
         * 若消息已经存在于传送队列中，就不加入了。
         * @param pUDkey 用户Key
         * @param msg 消息
         * @param type 类型：1控制消息；2媒体消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         * @throws InterruptedException 
         */
        public void putNotifyMsg(String userId, MsgNormal msg) throws InterruptedException {
            if (msg==null||userId==null||userId.trim().length()==0) return;
            LinkedBlockingQueue<Message> _userQueue=notifyMsg.get(userId);
            if (_userQueue==null) {
                _userQueue=new LinkedBlockingQueue<Message>();
                notifyMsg.put(userId, _userQueue);
            }
//            synchronized(_userQueue) {
//            }
            List<Message> removeMsg=new ArrayList<Message>();
            for (Message m: _userQueue) {
                if (m.equals(msg)) removeMsg.add(m);
            }
            for (Message m: removeMsg) {
                _userQueue.remove(m);
            }
            if (msg.getSendTime()==0) msg.setSendTime(System.currentTimeMillis());
            _userQueue.put(msg);

//            //唤醒所有的客户端读取线程
//            List<PushUserUDKey> al=sessionService.getActivedUserUDKs(userId);
//            if (al!=null&&!al.isEmpty()) {
//                for (PushUserUDKey _pUdk: al) {
//                    Object _lck=LCKMAP_pkuANDhadmsgtobesend.get(_pUdk);
//                    if (_lck==null) return;
//                    synchronized(_lck) {
//                        _lck.notifyAll();
//                    }
//                }
//            }
        }

        public Message pollNotifyMsg(PushUserUDKey pUdk, SocketHandler sh) throws InterruptedException {
            if (pUdk==null||sh==null) return null;

            Message m=null;
            //从发送队列取一条消息
//            boolean canRead=true;
//            synchronized(LOCK_usersocketMap) {
//                canRead=sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh));
//            }
//            if (canRead) {
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                LinkedBlockingQueue<Message> mQueue=notifyMsg.get(pUdk.getUserId());
                if (mQueue!=null) m=mQueue.poll();
            }
            return m;
        }

        //已发送队列处理
        /**
         * 放入已发送队列
         * @param pUdk 用户设备Key
         * @param msg 消息（可以是控制消息，也可以是媒体消息）
         */
        public void addSendedNeedCtlAffirmMsg(PushUserUDKey pUdk, Message msg) {
            if (pUdk==null||msg==null) return ;
            if ((msg instanceof MsgMedia)&&(acc.get_M_Type()==0)) return ;
            if ((msg instanceof MsgNormal)&&(acc.get_N_Type()==0)) return ;

            LinkedBlockingQueue<Map<String, Object>> mq=sendedNeedCtlAffirmMsg.get(pUdk);
            if (mq==null) {
                mq=new LinkedBlockingQueue<Map<String, Object>>();
                sendedNeedCtlAffirmMsg.put(pUdk, mq);
            }
            //看看有无重复
            boolean canAdd=true;
            if (mq.size()>0) {
                for (Map<String, Object> _m: mq) {
                    Message _msg=(Message)_m.get("message");
                    if (_msg!=null) {
                        if ((_msg instanceof MsgNormal)&&(msg instanceof MsgMedia)) {
                            if (((MsgNormal)_msg).getMsgId().equals(((MsgNormal)msg).getMsgId())) {
                                canAdd=false;
                                break;
                            }
                        }
                        if ((_msg instanceof MsgMedia)&&(msg instanceof MsgMedia)) {
                            if (((MsgMedia)_msg).getTalkId().equals(((MsgMedia)msg).getTalkId())
                              &&((MsgMedia)_msg).getSeqNo()==((MsgMedia)msg).getSeqNo()) {
                                canAdd=false;
                                break;
                            }
                        }
                    }
                }
            }
            if (canAdd) {
                Map<String, Object> m=new HashMap<String, Object>();
                m.put("firstSendTime", msg.getSendTime());
                m.put("message", msg);
                m.put("sendSum", 1);
                m.put("flag", 1); //正在传输
                mq.add(m);//加入内存
                //TODO 数据库处理
            }
        }
        /**
         * 更新已发送队列
         * @param pUdk 用户设备Key
         * @param msg 消息（可以是控制消息，也可以是媒体消息）
         */
        public void updateSendedNeedCtlAffirmMsg(PushUserUDKey pUdk, Map<String, Object> sendedMap) {
            if (pUdk==null||sendedMap==null||sendedMap.isEmpty()||sendedMap.get("message")==null) return ;
            Message m=(Message)sendedMap.get("message");
            if ((m instanceof MsgMedia)&&(acc.get_M_Type()==0)) return;
            if ((m instanceof MsgNormal)&&(acc.get_N_Type()==0)) return;

            LinkedBlockingQueue<Map<String, Object>> mq=sendedNeedCtlAffirmMsg.get(pUdk);
            if (mq==null) {
                mq=new LinkedBlockingQueue<Map<String, Object>>();
                sendedNeedCtlAffirmMsg.put(pUdk, mq);
            }
            //看看有无重复
            int tmpI=0;
            boolean canAdd=true;
            if (mq.size()>0) {
                for (Map<String, Object> _m: mq) {
                    Message _msg=(Message)_m.get("message");
                    if (_msg!=null) {
                        if ((_msg instanceof MsgNormal)&&(m instanceof MsgMedia)) {
                            if (((MsgNormal)_msg).getMsgId().equals(((MsgNormal)m).getMsgId())) {
                                canAdd=false;
                                try {
                                    tmpI=(Integer)_m.get("sendSum");
                                    tmpI++;
                                } catch(Exception e) {
                                    tmpI=1;
                                }
                                _m.put("sendSum", tmpI);
                                break;
                            }
                        }
                        if ((_msg instanceof MsgMedia)&&(m instanceof MsgMedia)) {
                            if (((MsgMedia)_msg).getTalkId().equals(((MsgMedia)m).getTalkId())
                              &&((MsgMedia)_msg).getSeqNo()==((MsgMedia)m).getSeqNo()) {
                                canAdd=false;
                                try {
                                    tmpI=(Integer)_m.get("sendSum");
                                    tmpI++;
                                } catch(Exception e) {
                                    tmpI=1;
                                }
                                _m.put("sendSum", tmpI);
                                break;
                            }
                        }
                    }
                }
            }
            if (canAdd) {
                try {
                    tmpI=(Integer)sendedMap.get("sendSum");
                    tmpI++;
                } catch(Exception e) {
                    tmpI=1;
                }
                sendedMap.put("sendSum", tmpI);
                //TODO 更新数据库
                mq.add(sendedMap);
            }
        }

        /**
         * 得到需要重新传送的消息队列，包括音频消息
         * @param pUdk
         * @param sh
         * @return
         */
        public LinkedBlockingQueue<Map<String, Object>> getResendMsg(PushUserUDKey pUdk, SocketHandler sh) {
            if (pUdk==null||sh==null) return null;

            LinkedBlockingQueue<Map<String, Object>> mq=new LinkedBlockingQueue<Map<String, Object>>();
            //从发送队列取一条消息
//            boolean canRead=true;
//            synchronized(LOCK_usersocketMap) {
//                canRead=sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh));
//            }
//            if (canRead) {
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                LinkedBlockingQueue<Map<String, Object>> _mq=sendedNeedCtlAffirmMsg.get(pUdk);
                int tmpI=0;
                long tmpL=0l;
                while (_mq!=null&&!_mq.isEmpty()) {
                    Map<String, Object> _m=_mq.poll();
                    if (_m==null||_m.isEmpty()) continue;

                    Object _msg=_m.get("message");
                    if (_msg instanceof MsgNormal) {
                        if (acc.get_N_Type()==1) {
                            try {
                                tmpI=(Integer)_m.get("sendSum");
                            } catch(Exception e) {
                                tmpI=1;
                            }
                            if (tmpI>acc.get_N_ExpireLimit()) {//不发了
                                _m.put("flag", 2);//由于超过过期发送次数，不发了
                                //TODO 更新数据库
                            } else mq.add(_m);
                        } else if (acc.get_N_Type()==2) {
                            try {
                                tmpL=(Long)_m.get("firstSendTime");
                            } catch(Exception e) {
                                tmpL=0;
                            }
                            if (System.currentTimeMillis()-tmpL>acc.get_N_ExpireTime()) {//不发了
                                _m.put("flag", 3);//由于超过过期时间，不发了
                                //TODO 更新数据库
                            } else mq.add(_m);
                        } else if (acc.get_N_Type()==3) {
                            try {
                                tmpI=(Integer)_m.get("sendSum");
                            } catch(Exception e) {
                                tmpI=-1;
                            }
                            try {
                                tmpL=(Long)_m.get("firstSendTime");
                            } catch(Exception e) {
                                tmpL=0;
                            }
                            if (tmpI>acc.get_N_ExpireLimit()) {//不发了
                                _m.put("flag", 2);//由于超过过期发送次数，不发了
                            } else if (System.currentTimeMillis()-tmpL>acc.get_N_ExpireTime()) {//不发了
                                _m.put("flag", 3);
                            }
                            try {
                                tmpI=(Integer)_m.get("flag");
                            } catch(Exception e) {
                                tmpI=0;
                            }
                            if (tmpI==2||tmpI==3) {
                                //TODO 更新数据库
                            } else mq.add(_m);
                        }
                    }
                    if (_msg instanceof MsgMedia) {
                        if (acc.get_M_Type()==1) {
                            try {
                                tmpI=(Integer)_m.get("sendSum");
                            } catch(Exception e) {
                                tmpI=-1;
                            }
                            if (tmpI>acc.get_M_ExpireLimit()) {//不发了
                                _m.put("flag", 2);//由于超过过期发送次数，不发了
                                //TODO 更新数据库
                            } else mq.add(_m);
                        } else if (acc.get_M_Type()==2) {
                            try {
                                tmpL=(Long)_m.get("firstSendTime");
                            } catch(Exception e) {
                                tmpL=0;
                            }
                            if (System.currentTimeMillis()-tmpL>acc.get_M_ExpireTime()) {//不发了
                                _m.put("flag", 3);//由于超过过期时间，不发了
                                //TODO 更新数据库
                            } else mq.add(_m);
                        } else if (acc.get_M_Type()==3) {
                            try {
                                tmpI=(Integer)_m.get("sendSum");
                            } catch(Exception e) {
                                tmpI=-1;
                            }
                            try {
                                tmpL=(Long)_m.get("firstSendTime");
                            } catch(Exception e) {
                                tmpL=0;
                            }
                            if (tmpI>acc.get_M_ExpireLimit()) {//不发了
                                _m.put("flag", 2);//由于超过过期发送次数，不发了
                            } else if (System.currentTimeMillis()-tmpL>acc.get_M_ExpireTime()) {//不发了
                                _m.put("flag", 3);
                            }
                            try {
                                tmpI=(Integer)_m.get("flag");
                            } catch(Exception e) {
                                tmpI=0;
                            }
                            if (tmpI==2||tmpI==3) {
                                //TODO 更新数据库
                            } else mq.add(_m);
                        }
                    }
                }
            }
            return (mq==null||mq.isEmpty())?null:mq;
        }

        /**
         * 删除指定用户的点对点对讲发送信息
         * @param pUdk
         */
        public void cleanMsg4Call(PushUserUDKey pUdk, String callId) {
            if (pUdk!=null) {
                LinkedBlockingQueue<Message> userMsgQueue=send2DeviceMsgCTL.get(pUdk);
                cleanOneUserMsgQueue4Call(userMsgQueue, callId);
                send2DeviceMsgCTL.remove(pUdk);

                userMsgQueue=send2DeviceMsgMDA.get(pUdk);
                cleanOneUserMsgQueue4Call(userMsgQueue, callId);
                send2DeviceMsgMDA.remove(pUdk);
            }
        }
        private void cleanOneUserMsgQueue4Call(LinkedBlockingQueue<Message> umq, String callId) {
            if (umq!=null&&!umq.isEmpty()) {
                for (Message m: umq) {
                    if (m instanceof MsgNormal) {
                        MsgNormal mn=(MsgNormal)m;
                        if (callId.equals(((MapContent)mn.getMsgContent()).get("CallId")+"")) {
                            umq.remove(m);
                        }
                    }
                    if (m instanceof MsgMedia) {
                        MsgMedia mm=(MsgMedia)m;
                        if (mm.getBizType()==2&&callId.equals(mm.getChannelId())) {
                            umq.remove(m);
                        }
                    }
                }
            }
        }

        /**
         * 删除指定对讲(会议)组相关的数据
         * @param om 对讲(会议)组对象
         */
        public void cleanMsg4Intercom(OneMeet om) {
            if (om!=null&&!StringUtils.isNullOrEmptyOrSpace(om.getGroupId())) {
                Map<String, UserPo> onlineMap=om.getEntryGroupUserMap();
                for (String userId: onlineMap.keySet()) {
                    List<PushUserUDKey> al=sessionService.getActivedUserUDKs(userId);
                    if (al!=null&&!al.isEmpty()) {
                        for (PushUserUDKey _pUdk: al) {
                            LinkedBlockingQueue<Message> userMsgQueue=send2DeviceMsgCTL.get(_pUdk);
                            cleanOneUserMsgQueue4Intercom(userMsgQueue, om);
                            send2DeviceMsgCTL.remove(_pUdk);

                            userMsgQueue=send2DeviceMsgMDA.get(_pUdk);
                            cleanOneUserMsgQueue4Intercom(userMsgQueue, om);
                            send2DeviceMsgMDA.remove(_pUdk);
                        }
                    }
                }
            }
        }
    }
    private void cleanOneUserMsgQueue4Intercom(LinkedBlockingQueue<Message> umq, OneMeet om) {
        if (umq!=null&&!umq.isEmpty()) {
            for (Message m: umq) {
                if (m instanceof MsgNormal) {
                    MsgNormal mn=(MsgNormal)m;
                    if (om.getGroupId().equals(((MapContent)mn.getMsgContent()).get("GroupId")+"")) {
                        umq.remove(m);
                    }
                }
                if (m instanceof MsgMedia) {
                    MsgMedia mm=(MsgMedia)m;
                    if (mm.getBizType()==2&&om.getGroupId().equals(mm.getChannelId())) {
                        umq.remove(m);
                    }
                }
            }
        }
    }

    /**
     * 内部类，用户组数据处理类
     * 用户组数据只有在初始化时从数据库中获取。
     * 其他数据更新都通过消息接口处理。
     * @author wanghui
     */
    public class UserAndGroupMemory {
        /**
         * 从数据库装载信息
         */
        public void loadFromDB() {
            groupService.fillGroupsAndUsers(groupMap, userMap);
        }

        /**
         * 通过用户Id获得用户信息
         * @param userId 用户Id
         * @return 用户信息
         */
        public UserPo getUserById(String userId) {
            if (StringUtils.isNullOrEmptyOrSpace(userId)) return null;

            UserPo ret=userMap.get(userId);
            if (ret==null) {
                ret=userService.getUserById(userId);
                if (ret!=null) userMap.put(userId, ret);
            }
            return ret;
        }

        /**
         * 通过用户Id获得用户信息
         * @param userId 用户Id
         * @return 用户信息
         */
        public Group getGroupById(String groupId) {
            if (StringUtils.isNullOrEmptyOrSpace(groupId)) return null;

            Group ret=groupMap.get(groupId);
            if (ret==null) {
                ret=groupService.getGroup(groupId);
                if (ret!=null) {
                    List<UserPo> userList=ret.getUserList();
                    if (userList!=null&&!userList.isEmpty()) {
                        for (int i=0; i<userList.size(); i++) {
                            UserPo up=userList.get(i);
                            if (userMap.get(up.getUserId())==null) {
                                userMap.put(up.getUserId(), up);
                            } else {
                                userList.remove(i);
                                userList.add(i, userMap.get(up.getUserId()));
                            }
                        }
                    }
                    groupMap.put(groupId, ret);
                }
            }
            return ret;
        }

        /**
         * 从数据库中读取数据，更新缓存。这里仅更新用户组本身的数据信息
         * @param groupId 用户组Id
         * @return 若用户组不存在，返回false
         */
        public boolean updateGroup(String groupId) {
            if (StringUtils.isNullOrEmptyOrSpace(groupId)) return false;

            GroupPo gp=groupService.getGroupPo(groupId);
            if (gp==null) return false;
            Group g=groupMap.get(groupId);
            if (g==null) {
                g=getGroupById(groupId);
                if (g==null) return false;
                groupMap.put(g.getGroupId(), g);
            } else {
                g.buildFromPo(gp);
            }
            return true;
        }

        /**
         * 删除缓存中的用户组
         * @param groupId 用户组Id
         * @return 若内存中不存在该用户组返回false
         */
        public boolean delGroup(String groupId) {
            if (StringUtils.isNullOrEmptyOrSpace(groupId)) return false;

            return groupMap.remove(groupId)!=null;
        }

        /**
         * 把用户信息加入用户组
         * @param groupId 用户组Id
         * @param userId 用户Id
         */
        public boolean addUserToGroup(String groupId, String userId) {
            if (StringUtils.isNullOrEmptyOrSpace(groupId)) return false;
            if (StringUtils.isNullOrEmptyOrSpace(userId)) return false;

            Group g=getGroupById(groupId);
            if (g==null) return false;

            UserPo up=null;
            List<UserPo> upl=g.getUserList();
            if (upl==null) {
                up=getUserById(userId);
                if (up!=null) {
                    upl=new ArrayList<UserPo>();
                    upl.add(up);
                    g.setUserList(upl);
                } else return false;
            } else {
                boolean find=false;
                for (int i=0; i<upl.size(); i++) {
                    if (userId.equals(upl.get(i).getUserId())) {
                        find=true;
                        break;
                    }
                }
                if (!find) {
                    up=getUserById(userId);
                    if (up!=null) upl.add(up);
                    else return false;
                }
            }
            return true;
        }

        /**
         * 把用户从用户组中删除
         * @param groupId 用户组Id
         * @param userId 用户Id
         */
        public boolean delUserFromGroup(String groupId, String userId) {
            if (StringUtils.isNullOrEmptyOrSpace(groupId)) return false;
            if (StringUtils.isNullOrEmptyOrSpace(userId)) return false;

            Group g=getGroupById(groupId);
            if (g==null) return false;

            List<UserPo> upl=g.getUserList();
            if (upl==null) return false;

            boolean find=false;
            for (int i=upl.size()-1; i>=0; i--) {
                if (userId.equals(upl.get(i).getUserId())) {
                    find=true;
                    upl.remove(i);
                    break;
                }
            }
            return find;
        }
    }
}