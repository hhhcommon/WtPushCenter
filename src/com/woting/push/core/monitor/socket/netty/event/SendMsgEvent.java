package com.woting.push.core.monitor.socket.netty.event;

/**
 * 发送消息事件
 * @author wanghui
 */
public class SendMsgEvent {
    //有控制消息需要发送的事件
    public static final SendMsgEvent CONTROLMSG_TOBESEND_EVENT=new SendMsgEvent(SendMsgType.SEND_CONTROL);
    //有媒体消息需要发送的事件
    public static final SendMsgEvent MEDISMSG_TOBESEND_EVENT=new SendMsgEvent(SendMsgType.SEND_MEDIA);
    //有通知消息需要发送的事件
    public static final SendMsgEvent NOTIFYMSG_TOBESEND_EVENT=new SendMsgEvent(SendMsgType.SEND_NOTIFY);
    //有重发消息需要发送的事件
    public static final SendMsgEvent RESENDMSG_TOBESEND_EVENT=new SendMsgEvent(SendMsgType.SEND_RESEND);

    private final SendMsgType sendMsgType;

    /**
     * 构造函数
     * @param sendMsgType 消息类型
     */
    protected SendMsgEvent(SendMsgType sendMsgType) {
        this.sendMsgType=sendMsgType;
    }

    /**
     * 得到该事件的类型
     * @return 时间类型
     */
    public SendMsgType sendMsgType() {
        return sendMsgType;
    }
}