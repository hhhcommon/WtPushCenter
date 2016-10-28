package com.woting.push.config;

/**
 * 每个socket通道监控配置。
 * <pre>
 * 每个Socket连接都可以拥有自己的配置，但目前版本只实现所有Socket连接都采用相同的配置
 * </pre>
 * <br/>
 * 在设置类，所有字段前都有_
 * @author wanghui
 */
public class SocketHandleConfig implements Config {
    /**
     * 多长时间没有收到信息，若大于此时间没有获得信息，则系统认为Socket已经失效，将关闭相应的处理
     */
    private long _ExpireTime=1000*240;
    /**
     * 主监控进程监控周期
     */
    private long _MonitorDelay=1000;
    /**
     * 尝试销毁次数，大于此数量仍未达到销毁条件，则强制销毁
     */
    private long _TryDestoryAllCount=5;
    /**
     * 多长时间发送一次心跳
     */
    private long _BeatDelay=40*1000;
    /**
     *  接收消息处理中，连续收到错误|异常消息的次数，若大于这个数量，则系统将认为此Socket为恶意连接，将关闭相应的处理
     */
    private long _RecieveErr_ContinueCount=3;
    /**
     *  接收消息处理中，总共收到错误|异常消息的次数，若大于这个数量，则系统将认为此Socket为恶意连接，将关闭相应的处理
     */
    private long _RecieveErr_SumCount=100;

    public long get_ExpireTime() {
        return _ExpireTime;
    }
    public void set_ExpireTime(long _ExpireTime) {
        this._ExpireTime = _ExpireTime;
    }
    public long get_MonitorDelay() {
        return _MonitorDelay;
    }
    public void set_MonitorDelay(long _MonitorDelay) {
        this._MonitorDelay = _MonitorDelay;
    }
    public long get_TryDestoryAllCount() {
        return _TryDestoryAllCount;
    }
    public void set_TryDestoryAllCount(long _TryDestoryAllCount) {
        this._TryDestoryAllCount = _TryDestoryAllCount;
    }
    public long get_BeatDelay() {
        return _BeatDelay;
    }
    public void set_BeatDelay(long _BeatDelay) {
        this._BeatDelay = _BeatDelay;
    }
    public long get_RecieveErr_ContinueCount() {
        return _RecieveErr_ContinueCount;
    }
    public void set_RecieveErr_ContinueCount(long _RecieveErr_ContinueCount) {
        this._RecieveErr_ContinueCount = _RecieveErr_ContinueCount;
    }
    public long get_RecieveErr_SumCount() {
        return _RecieveErr_SumCount;
    }
    public void set_RecieveErr_SumCount(long _RecieveErr_SumCount) {
        this._RecieveErr_SumCount = _RecieveErr_SumCount;
    }
}