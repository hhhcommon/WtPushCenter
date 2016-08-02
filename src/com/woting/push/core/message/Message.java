package com.woting.push.core.message;

/**
 * 消息接口，主要是消息序列化和反序列化。
 * @author wanghui
 */
public abstract class Message implements Comparable<Message>{
    public final static byte[] END_FIELD={124, 124}; //字段结束标识||
    public final static byte[] END_HEAD={94, 94}; //消息头结束标识
    public final static byte[] END_MSG={124, 94}; //消息结束标识
    
    protected int msgType; //消息类型:0主动发出；1回复类型
    protected int affirm; //是否需要确认;0不需要1需要，默认值=0不需要确认
    protected long sendTime; //发送时间

    //1服务器；0设备
    protected int fromType; //从那类设备来
    protected int toType; //到那类设备去

    public int getMsgType() {
        return msgType;
    }
    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }

    public int getAffirm() {
        return affirm;
    }
    public void setAffirm(int affirm) {
        this.affirm = affirm;
    }

    public long getSendTime() {
        return sendTime;
    }
    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public int getFromType() {
        return fromType;
    }
    public void setFromType(int fromType) {
        this.fromType = fromType;
    }

    public int getToType() {
        return toType;
    }
    public void setToType(int toType) {
        this.toType = toType;
    }

    /**
     * 该条消息是否需要确认
     * @return 需要确认返回true，否则返回false
     */
    public boolean isAffirm() {
        return affirm==1;
    }

    /**
     * 用于消息排序
     */
    @Override
    public int compareTo(Message o) {
        long flag=sendTime-o.getSendTime();
        if (flag==0) return 0;
        if (flag>0) return 1;
        return -1;
    }

    /**
     * 从字节数组中获得消息
     */
    public abstract void fromBytes(byte[] binaryMsg);

    /**
     * 把消息序列化为字节数组
     * @return 消息对应的字节数组
     */
    public abstract byte[] toBytes();

    /**
     * 判断是否是应答消息
     */
    public abstract boolean isAck();
}