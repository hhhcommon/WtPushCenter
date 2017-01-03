package com.woting.push.core.mem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
import com.woting.push.config.MediaExpiredConfig;
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
        mec=(MediaExpiredConfig)SystemCache.getCache(PushConstants.MEXPIRED_CONF).getContent();

        groupMap=new ConcurrentHashMap<String, Group>();
        userMap=new ConcurrentHashMap<String, UserPo>();

        pureMsgQueue=new ConcurrentLinkedQueue<Message>();
        typeMsg=new ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>>();

        send2DeviceMsg=new ConcurrentHashMap<PushUserUDKey, ConcurrentLinkedQueue<Message>>();
        notifyMsg=new ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>>();
        sendedNeedCtlAffirmMsg=new ConcurrentHashMap<PushUserUDKey, ConcurrentLinkedQueue<Map<String, Object>>>();

        REF_deviceANDsocket=new HashMap<String, SocketHandler>();
        REF_userdtypeANDsocket=new HashMap<String, SocketHandler>();
        REF_userdtypeANDudk=new HashMap<String, PushUserUDKey>();
        REF_udkANDsocket=new HashMap<PushUserUDKey, SocketHandler>();
        REF_socketANDudk=new HashMap<SocketHandler, PushUserUDKey>();

        receiveMem=new ReceiveMemory();
        sendMem=new SendMemory();
        uANDgMem=new UserAndGroupMemory();

        uANDgMem.loadFromDB();
        //TODO del
        System.out.println("===========================");
        //TODO del
    }

    //==========其他配置信息
    private AffirmCtlConfig acc;
    private MediaExpiredConfig mec;
    //==========接收消息内存
    /**
     * 组信息缓存，目前缓存所有组信息
     */
    private ConcurrentHashMap<String, Group> groupMap;
    /**
     * 组信息缓存，目前缓存所有组信息
     */
    private ConcurrentHashMap<String, UserPo> userMap;
    /**
     * 总接收(纯净)队列所有收到的信息都会暂时先放入这个队列中
     */
    private ConcurrentLinkedQueue<Message> pureMsgQueue;
    /**
     * 分类接收队列，不同类型的消息会由不同类去处理
     * <pre>
     * Key    分类的描述
     * Value  是要处理的队列
     * </pre>
     */
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>> typeMsg;
    //==========发送消息内存
    /**
     * 发送消息队列——发送给具体的设备
     * <pre>
     * Key    发送消息的目标，用户的Key
     * Value  消息队列
     * </pre>
     */
    private ConcurrentHashMap<PushUserUDKey, ConcurrentLinkedQueue<Message>> send2DeviceMsg;
    /**
     * 给用户发送的消息队列——通知消息
     * <pre>
     * Key    用户Id
     * Value  消息队列
     * </pre>
     */
    private ConcurrentHashMap<String, ConcurrentLinkedQueue<Message>> notifyMsg;
    
    /**
     * 已发送的需要控制回复的消息，此类消息与设备号绑定
     * <pre>
     * Key    用户Id
     * Value  消息Map:FirstSendTime,Message
     * </pre>
     */
    private ConcurrentHashMap<PushUserUDKey, ConcurrentLinkedQueue<Map<String, Object>>> sendedNeedCtlAffirmMsg;

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
            return true;
        } else {
            SocketHandler _sh=REF_deviceANDsocket.get(pUdk.getDeviceId()+"::"+pUdk.getPCDType());
            if (_sh!=null) {
                if (!sh.equals(_sh)) return false;
                return true;
            } else {
                REF_deviceANDsocket.put(pUdk.getDeviceId()+"::"+pUdk.getPCDType(), sh);
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
        public boolean addPureMsg(Message msg) {
            return pureMsgQueue==null?false:pureMsgQueue.offer(msg);
        }

        /**
         * 从总接收队列中获得并移除第一个元素
         * @return 从队列中获取的元素
         */
        public Message pollPureMsg() {
            return pureMsgQueue==null?null:pureMsgQueue.poll();
        }

        /**
         * 加入分类处理的消息队列
         * @param type 分类标志
         * @param msg 消息
         */
        public boolean addTypeMsg(String type, Message msg) {
            ConcurrentLinkedQueue<Message> typeDeque=typeMsg.get(type);
            if (typeDeque==null) {
                synchronized(typeMsg) {
                    typeDeque=new ConcurrentLinkedQueue<Message>();
                    typeMsg.put(type, typeDeque);
                }
            }
            return typeDeque.offer(msg);
        }

        /**
         * 从分类处理的队列获取消息，并从该队列移除消息
         * @param type 分类标志
         * @return 消息
         */
        public Message pollTypeMsg(String type) {
            if (typeMsg==null||typeMsg.get(type)==null) return null;
            return typeMsg.get(type).poll();
        }
    }

    /**
     * 内部类，发送消息处理类
     * @author wanghui
     */
    public class SendMemory {
        /**
         * [消息队列-直接对应（设备+用户）]
         * 把消息(msg)加入所对应用户设备(pUDkey)的传送队列<br/>
         * 若消息已经存在于传送队列中，就不加入了。
         * @param pUDkey 用户Key
         * @param msg 消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         */
        public boolean addDeviceMsg(PushUserUDKey pUDkey, Message msg) {
            if (send2DeviceMsg==null||msg==null||pUDkey==null) return false;
            ConcurrentLinkedQueue<Message> _deviceQueue=send2DeviceMsg.get(pUDkey);
            if (_deviceQueue==null) {
                _deviceQueue=new ConcurrentLinkedQueue<Message>();
                send2DeviceMsg.put(pUDkey, _deviceQueue);
            }
            if (msg.getSendTime()==0) msg.setSendTime(System.currentTimeMillis());
            //查找相同的消息是否存在
            boolean _exist=false;
            for (Message _msg: _deviceQueue) {
                if (_msg.equals(msg)) {
                    _exist=true;
                    break;
                }
            }
            if (_exist) return true;
            return _deviceQueue.offer(msg);
        }

        /**
         * [消息队列-直接对应（设备+用户）]
         * 获得设备用户的发送消息<br/>
         * @param pUDkey 用户Key
         * @param msg 消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         */
        public Message getDeviceMsg(PushUserUDKey pUdk, SocketHandler sh) {
            if (pUdk==null||sh==null) return null;

            Message m=null;
            //从发送队列取一条消息
//            boolean canRead=true;
//            synchronized(LOCK_usersocketMap) {
//                canRead=sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh));
//            }
//            if (canRead) {
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                Queue<Message> mQueue=send2DeviceMsg.get(pUdk);
                if (mQueue!=null) m=mQueue.poll();
                //检查是否过期
                if (m!=null&&(m instanceof MsgMedia)) {
                    if (((MsgMedia)m).isAudio()&&(System.currentTimeMillis()-((MsgMedia)m).getSendTime())>mec.get_AudioExpiredTime()) m=null;
                    else
                    if (((MsgMedia)m).isVedio()&&(System.currentTimeMillis()-((MsgMedia)m).getSendTime())>mec.get_VedioExpiredTime()) m=null;
                }
            }
            return m;
        }

        public boolean addNotifyMsg(String userId, Message msg) {
            if (msg==null||userId==null||userId.trim().length()==0) return false;
            ConcurrentLinkedQueue<Message> _userQueue=notifyMsg.get(userId);
            if (_userQueue==null) {
                _userQueue=new ConcurrentLinkedQueue<Message>();
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
            return _userQueue.add(msg);
        }

        public Message getNotifyMsg(PushUserUDKey pUdk, SocketHandler sh) {
            if (pUdk==null||sh==null) return null;

            Message m=null;
            //从发送队列取一条消息
//            boolean canRead=true;
//            synchronized(LOCK_usersocketMap) {
//                canRead=sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh));
//            }
//            if (canRead) {
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                Queue<Message> mQueue=notifyMsg.get(pUdk.getUserId());
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
        public boolean addSendedNeedCtlAffirmMsg(PushUserUDKey pUdk, Message msg) {
            if (pUdk==null||msg==null) return false;
            if ((msg instanceof MsgMedia)&&(acc.get_M_Type()==0)) return false;
            if ((msg instanceof MsgNormal)&&(acc.get_N_Type()==0)) return false;

            ConcurrentLinkedQueue<Map<String, Object>> mq=sendedNeedCtlAffirmMsg.get(pUdk);
            if (mq==null) {
                mq=new ConcurrentLinkedQueue<Map<String, Object>>();
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
            return canAdd;
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

            ConcurrentLinkedQueue<Map<String, Object>> mq=sendedNeedCtlAffirmMsg.get(pUdk);
            if (mq==null) {
                mq=new ConcurrentLinkedQueue<Map<String, Object>>();
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
        public ConcurrentLinkedQueue<Map<String, Object>> getResendMsg(PushUserUDKey pUdk, SocketHandler sh) {
            if (pUdk==null||sh==null) return null;

            ConcurrentLinkedQueue<Map<String, Object>> mq=new ConcurrentLinkedQueue<Map<String, Object>>();
            //从发送队列取一条消息
//            boolean canRead=true;
//            synchronized(LOCK_usersocketMap) {
//                canRead=sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh));
//            }
//            if (canRead) {
            if (sh.equals(REF_udkANDsocket.get(pUdk))||pUdk.equals(REF_socketANDudk.get(sh))) {
                ConcurrentLinkedQueue<Map<String, Object>> _mq=sendedNeedCtlAffirmMsg.get(pUdk);
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
                                //TODO 跟新数据库
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
                                //TODO 跟新数据库
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
                ConcurrentLinkedQueue<Message> userMsgQueue=send2DeviceMsg.get(pUdk);
                if (userMsgQueue!=null&&!userMsgQueue.isEmpty()) {
                    synchronized(userMsgQueue) {
                        for (Message m: userMsgQueue) {
                            if (m instanceof MsgNormal) {
                                MsgNormal mn=(MsgNormal)m;
                                if (callId.equals(((MapContent)mn.getMsgContent()).get("CallId")+"")) {
                                    userMsgQueue.remove(m);
                                }
                            }
                            if (m instanceof MsgMedia) {
                                MsgMedia mm=(MsgMedia)m;
                                if (mm.getBizType()==2&&callId.equals(mm.getObjId())) {
                                    userMsgQueue.remove(m);
                                }
                            }
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
                            ConcurrentLinkedQueue<Message> userMsgQueue=send2DeviceMsg.get(_pUdk);
                            if (userMsgQueue!=null&&!userMsgQueue.isEmpty()) {
                                synchronized(userMsgQueue) {
                                    for (Message m: userMsgQueue) {
                                        if (m instanceof MsgNormal) {
                                            MsgNormal mn=(MsgNormal)m;
                                            if (om.getGroupId().equals(((MapContent)mn.getMsgContent()).get("GroupId")+"")) {
                                                userMsgQueue.remove(m);
                                            }
                                        }
                                        if (m instanceof MsgMedia) {
                                            MsgMedia mm=(MsgMedia)m;
                                            if (mm.getBizType()==2&&om.getGroupId().equals(mm.getObjId())) {
                                                userMsgQueue.remove(m);
                                            }
                                        }
                                    }
                                }
                            }
                        }
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