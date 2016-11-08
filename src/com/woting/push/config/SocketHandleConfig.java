package com.woting.push.config;

/**
 * 每个socket通道监控配置。
 * <pre>
 * 每个Socket连接都可以拥有自己的配置，但目前版本只实现所有Socket连接都采用相同的配置
 * </pre>
 * <br/>
 * 在配置类，所有字段前都有_
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
    private long _TryDestoryAllCount=100;
    /**
     * 多长时间发送一次心跳
     */
    private long _BeatDelay=40*1000;
    /**
     * 处理中，连续错误|异常消息的次数，若大于这个数量，则系统将认为此Socket为恶意连接，将关闭相应的处理
     */
    private long _Err_ContinueCount=3;
    /**
     * 处理中，总共错误|异常消息的次数，若大于这个数量，则系统将认为此Socket为恶意连接，将关闭相应的处理
     */
    private long _Err_SumCount=100;
    /**
     * 日志文件存储目录，若不设置或为空，则不进行日志存储
     */
    private String _LogPath=null;

    
    public long get_ExpireTime() {
        return _ExpireTime;
    }
    public void set_ExpireTime(long _ExpireTime) {
        this._ExpireTime=_ExpireTime;
    }
    public long get_MonitorDelay() {
        return _MonitorDelay;
    }
    public void set_MonitorDelay(long _MonitorDelay) {
        this._MonitorDelay=_MonitorDelay;
    }
    public long get_TryDestoryAllCount() {
        return _TryDestoryAllCount;
    }
    public void set_TryDestoryAllCount(long _TryDestoryAllCount) {
        this._TryDestoryAllCount=_TryDestoryAllCount;
    }
    public long get_BeatDelay() {
        return _BeatDelay;
    }
    public void set_BeatDelay(long _BeatDelay) {
        this._BeatDelay=_BeatDelay;
    }
    public long get_Err_ContinueCount() {
        return _Err_ContinueCount;
    }
    public void set_Err_ContinueCount(long _Err_ContinueCount) {
        this._Err_ContinueCount=_Err_ContinueCount;
    }
    public long get_Err_SumCount() {
        return _Err_SumCount;
    }
    public void set_Err_SumCount(long _Err_SumCount) {
        this._Err_SumCount=_Err_SumCount;
    }
    public String get_LogPath() {
        return _LogPath;
    }
    public void set_LogPath(String _LogPath) {
        this._LogPath=_LogPath;
    }
}