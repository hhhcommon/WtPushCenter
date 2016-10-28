package com.woting.push.config;

/**
 * 服务端配置信息
 * @author wanghui
 */
public class PushConfig implements Config {
    /**
     * 推送服务控制信号的tcp端口号
     */
    private int _controlTcpPort=15678;
    /**
     * 检查清除会话的间隔时间，毫秒//5分钟
     */
    private int _cleanInterval=5*60*1000;
    /**
     * 处理原生接收队列线程的个数
     */
    private int _dispatchThreadCount=4;

    public int get_ControlTcpPort() {
        return _controlTcpPort;
    }
    protected void set_ControlTcpPort(int port) {
        this._controlTcpPort=port;
    }

    public int get_CleanInterval() {
        return _cleanInterval;
    }
    protected void set_CleanInterval(int cleanInterval) {
        this._cleanInterval=cleanInterval;
    }

    public int get_DispatchThreadCount() {
        return _dispatchThreadCount;
    }
    protected void set_DsispatchThreadCount(int dispatchThreadCount) {
        this._dispatchThreadCount=dispatchThreadCount;
    }
}