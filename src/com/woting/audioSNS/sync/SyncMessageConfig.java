package com.woting.audioSNS.sync;

import com.woting.push.config.Config;

/**
 * 同步消息（用于业务系统与消息系统同步数据，目前主要是组信息）控制的配置。<br/>
 * 在配置类，所有字段前都有_
 * @author wanghui
 */
public class SyncMessageConfig implements Config {
    /**
     * 处理同步消息（用于业务系统与消息系统同步数据，目前主要是组信息）线程的个数，从原生队列中获取同步消息，并进行处理的线程的个数
     */
    private int _DealThreadCount=4;
    public int get_DealThreadCount() {
        return _DealThreadCount;
    }
    public void set_DealThreadCount(int _DealThreadCount) {
        this._DealThreadCount=_DealThreadCount;
    }
}