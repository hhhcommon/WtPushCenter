package com.woting.audioSNS.notify;

import com.woting.push.config.Config;

/**
 * 通知消息控制的配置。<br/>
 * 在配置类，所有字段前都有_
 * @author wanghui
 */
public class NotifyMessageConfig implements Config {
    /**
     * 处理通知消息线程的个数，从原生队列中获取通知消息，并进行处理的线程的个数
     */
    private int _DealThreadCount=4;
    public int get_DealThreadCount() {
        return _DealThreadCount;
    }
    public void set_DealThreadCount(int _DealThreadCount) {
        this._DealThreadCount=_DealThreadCount;
    }
}