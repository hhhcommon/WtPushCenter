package com.woting.push.core.message;

/**
 * 比较两个消息是否是相同的消息
 * @author wanghui
 */
public interface CompareMsg {
     public boolean compare(Message msg1, Message msg2);
}