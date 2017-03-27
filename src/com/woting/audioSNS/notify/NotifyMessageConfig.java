package com.woting.audioSNS.notify;

import com.woting.push.core.config.Config;

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

    /**
     * 发送间隔时间
     */
    private int _Delay=10*60*1000;
    public int get_Delay() {
        return _Delay;
    }
    public void set_Delay(int _Delay) {
        this._Delay = _Delay;
    }
//    "dealThread":"1",
//    "delay":"10*60*1000", //上次发送后，5分钟
//    "expireLimit":"10", //发送的最大次数
//    "expireTime":"5*24*60*60*1000", //过期时间，多长时间后未收到回执，这个消息被抛弃掉,-1是永远不过期
}