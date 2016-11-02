package com.woting.push.core.mem;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    /*
     * 总接收队列所有收到的信息都会暂时先放入这个队列中
     */
    private ConcurrentLinkedQueue<Message> pureMsgQueue;
    /*
     * 分类接收队列，不同类型的消息会由不同类去处理
     * <pre>
     * Key是分类的描述：
     * Value是要处理的队列
     * </pre>
     */
    private ConcurrentHashMap<String, ConcurrentLinkedDeque<Message>> typeMsgMap; 

    public ReceiveMemory receiveMem=null;
    /*
     * 初始化，创建两个主要的对象
     */
    private TcpGlobalMemory() {
       this.pureMsgQueue=new ConcurrentLinkedQueue<Message>();
       this.typeMsgMap=new ConcurrentHashMap<String, ConcurrentLinkedDeque<Message>>();
       receiveMem=new ReceiveMemory();
    }

    /**
     * 绑定用户和Socket
     * @param pUk
     * @param sHandler
     */
    public void bindPushUserANDSocket(PushUserUDKey pUk, SocketHandler sHandler) {        
    }

    public class ReceiveMemory {
        public boolean addPureQueue(Message msg) {
            return pureMsgQueue.offer(msg);
        }
    }
}