package com.woting.audioSNS.calling;

import com.woting.push.config.Config;

/**
 * 电话处理的配置。<br/>
 * 在配置类，所有字段前都有_
 * @author wanghui
 */
public class CallingConfig implements Config {
    /**
     * 处理原生接收队列线程的个数，从原生队列中获取与电话处理的相关消息，并进行处理的线程的个数。此线程包括分发和创建处理线程
     */
    private int _DealThreadCount=2;
    /**
     * 检查是否在线的过期时间，毫秒
     */
    private long _ExpireOnline=10*1000;
    /**
     * 检查无应答的过期时间，毫秒
     */
    private long _ExpireAck=30*1000;
    /**
     * 检查一次通话的过期时间，毫秒
     */
    private long _ExpireTime=60*1000;

    public int get_DealThreadCount() {
        return _DealThreadCount;
    }
    public void set_DealThreadCount(int _DealThreadCount) {
        this._DealThreadCount=_DealThreadCount;
    }
    public long get_ExpireOnline() {
        return _ExpireOnline;
    }
    public void set_ExpireOnline(long _ExpireOnline) {
        this._ExpireOnline=_ExpireOnline;
    }
    public long get_ExpireAck() {
        return _ExpireAck;
    }
    public void set_ExpireAck(long _ExpireAck) {
        this._ExpireAck=_ExpireAck;
    }
    public long get_ExpireTime() {
        return _ExpireTime;
    }
    public void set_ExpireTime(long _ExpireTime) {
        this._ExpireTime=_ExpireTime;
    }
}