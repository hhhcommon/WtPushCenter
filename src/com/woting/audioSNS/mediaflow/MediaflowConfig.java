package com.woting.audioSNS.mediaflow;

/**
 * 流数据控制的配置。<br/>
 * 在配置类，所有字段前都有_
 * @author wanghui
 */
public class MediaflowConfig implements com.woting.push.core.config.Config {
    /**
     * 处理流数据线程的个数，从原生队列中获取与流相关的消息，并进行处理的线程的个数。此线程包括分发和创建处理线程
     */
    private int _DealThreadCount=4;
    public int get_DealThreadCount() {
        return _DealThreadCount;
    }
    public void set_DealThreadCount(int _DealThreadCount) {
        this._DealThreadCount=_DealThreadCount;
    }
}