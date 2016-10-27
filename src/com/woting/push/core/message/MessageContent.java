package com.woting.push.core.message;

import java.io.UnsupportedEncodingException;

/**
 * 消息体内容接口，这个接口要实现两个方法：
 * <pre>
 *   从字节数组中得到消息内容
 *   把自身转化为消息数据
 * </pre>
 * @author wanghui
 */
public interface MessageContent {
    /**
     * 从字节数组中获得消息内容
     * @throws UnsupportedEncodingException 
     */
    public abstract void fromBytes(byte[] content) throws UnsupportedEncodingException;

    /**
     * 把消息内容序列化为字节数组
     * @return 消息对应的字节数组
     * @throws UnsupportedEncodingException 
     */
    public abstract byte[] toBytes() throws UnsupportedEncodingException;
}