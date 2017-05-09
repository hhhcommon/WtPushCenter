package com.woting.push.core.monitor.socket.netty.event;

/**
 * 消息推送类型事件
 * @author wanghui
 */
public enum SendMsgType {
    SEND_CONTROL, //一般控制消息
    SEND_MEDIA,   //媒体消息
    SEND_NOTIFY  //通知消息
}