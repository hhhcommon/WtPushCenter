package com.woting.push.core.message;

/**
 * 比较两个消息是否是相同的消息
 * @author wanghui
 */
public interface CompareMsg<M extends Message> {
     public boolean compare(M msg1, M msg2);
}