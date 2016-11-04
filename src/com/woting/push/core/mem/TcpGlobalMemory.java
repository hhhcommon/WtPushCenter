package com.woting.push.core.mem;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.spiritdata.framework.util.JsonUtils;
import com.woting.push.core.message.Message;
import com.woting.push.core.monitor.socket.oio.SocketHandler;
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
public class TcpGlobalMemory {
    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static TcpGlobalMemory instance=new TcpGlobalMemory();
    }
    /**
     * 得到单例的对象
     * @return 接收消息对象
     */
    public static TcpGlobalMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    //==========接收消息内存
    /**
     * 总接收队列所有收到的信息都会暂时先放入这个队列中
     */
    private ConcurrentLinkedQueue<Message> pureMsgQueue;
    /**
     * 分类接收队列，不同类型的消息会由不同类去处理
     * <pre>
     * Key    分类的描述
     * Value  是要处理的队列
     * </pre>
     */
    private ConcurrentHashMap<String, ConcurrentLinkedDeque<Message>> typeMsg;
    //==========发送消息内存
    /**
     * 发送消息队列
     * <pre>
     * Key    发送消息的目标，用户的Key
     * Value  消息队列
     * </pre>
     */
    private ConcurrentHashMap<PushUserUDKey, ConcurrentLinkedQueue<Message>> sendMsg;

    //以下为用户和Socket的绑定关系
    private Object LOCK_udkANDsoket=new Object();//用户和Sockek对应的临界区的锁，这个锁是读写一致的，虽然慢，但能保证数据一致性
    /**
     * 用户和SocketHandler的对应关系
     * <pre>
     * Key    推送用户Key对象
     * Value  Socket处理线程类
     * </pre>
     */
    private Map<PushUserUDKey, SocketHandler> REF_udkANDsoket;

    public ReceiveMemory receiveMem=null;
    public SendMemory sendMem=null;
    /*
     * 初始化，创建两个主要的对象
     */
    private TcpGlobalMemory() {
       this.pureMsgQueue=new ConcurrentLinkedQueue<Message>();
       this.typeMsg=new ConcurrentHashMap<String, ConcurrentLinkedDeque<Message>>();
       this.REF_udkANDsoket=new ConcurrentHashMap<PushUserUDKey, SocketHandler>();
       receiveMem=new ReceiveMemory();
       sendMem=new SendMemory();
    }

    /**
     * 绑定用户和Socket
     * @param pUk 用户key
     * @param sh  SocketHandler处理线程
     */
    public void bindPushUserANDSocket(PushUserUDKey pUk, SocketHandler sh) {
        if (pUk==null||sh==null) return;
        synchronized(LOCK_udkANDsoket) {
            REF_udkANDsoket.put(pUk, sh);
        }
    }

    /**
     * 内部类，接受消息处理类
     * @author wanghui
     */
    public class ReceiveMemory {
        public boolean addPureMsg(Message msg) {
            return pureMsgQueue==null?false:pureMsgQueue.offer(msg);
        }

        public Message pollPureMsg() {
            return pureMsgQueue==null?null:pureMsgQueue.poll();
        }

        /**
         * 加入分类处理的消息队列
         * @param string 分类标志
         * @param m 消息
         */
        public boolean addTypeMsg(String type, Message msg) {
            ConcurrentLinkedDeque<Message> typeDeque=typeMsg.get(type);
            if (typeDeque==null) {
                synchronized(typeMsg) {
                    typeDeque=new ConcurrentLinkedDeque<Message>();
                    typeMsg.put(type, typeDeque);
                }
            }
            return typeDeque.offer(msg);
        }
    }

    /**
     * 内部类，发送消息处理类
     * @author wanghui
     */
    public class SendMemory {
        /**
         * 把消息(msg)加入所对应用户(pUDkey)的传送队列<br/>
         * 若消息已经存在于传送队列中，就不加入了。
         * @param pUDkey 用户Key
         * @param msg 消息
         * @return 加入成功返回true(若消息已经存在，也放回true)，否则返回false
         */
        public boolean addMsg(PushUserUDKey pUDkey, Message msg) {
            if (sendMsg==null||msg==null||pUDkey==null) return false;
            ConcurrentLinkedQueue<Message> _userQueue=sendMsg.get(pUDkey);
            if (_userQueue==null) {
                _userQueue=new ConcurrentLinkedQueue<Message>();
                sendMsg.put(pUDkey, _userQueue);
            }
            synchronized(_userQueue) {
                if (msg.getSendTime()==0) msg.setSendTime(System.currentTimeMillis());
                //查找相同的消息是否存在
                boolean _exist=false;
                for (Message _msg: _userQueue) {
                    _exist=JsonUtils.objToJson(msg).equals(JsonUtils.objToJson(_msg));
                    if (_exist) break;
                }
                if (_exist) return true;
                return _userQueue.offer(msg);
            }
        }
    }
}