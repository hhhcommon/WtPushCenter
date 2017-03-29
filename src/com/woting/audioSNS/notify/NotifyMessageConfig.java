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

    private int _ExpireLimit=10;
    public int get_ExpireLimit() {
        return _ExpireLimit;
    }
    public void set_ExpireLimit(int _ExpireLimit) {
        this._ExpireLimit = _ExpireLimit;
    }

    private long _ExpireTime=5*24*60*60*1000;//5天
    public long get_ExpireTime() {
        return _ExpireTime;
    }
    public void set_ExpireTime(long _ExpireTime) {
        this._ExpireTime = _ExpireTime;
    }
}