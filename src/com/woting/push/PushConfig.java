package com.woting.push;

import com.woting.push.config.Config;

/**
 * 服务端配置信息
 * @author wanghui
 */
public class PushConfig implements Config {
    /**
     * 推送服务控制信号的tcp端口号
     */
    private int _ControlTcpPort=15678;
    /**
     * 检查清除会话的间隔时间，毫秒//5分钟
     */
    private int _CleanInterval=5*60*1000;
    /**
     * 处理原生接收队列线程的个数
     */
    private int _DispatchThreadCount=4;

    public int get_ControlTcpPort() {
        return _ControlTcpPort;
    }
    public void set_ControlTcpPort(int port) {
        this._ControlTcpPort=port;
    }

    public int get_CleanInterval() {
        return _CleanInterval;
    }
    public void set_CleanInterval(int cleanInterval) {
        this._CleanInterval=cleanInterval;
    }

    public int get_DispatchThreadCount() {
        return _DispatchThreadCount;
    }
    public void set_DsispatchThreadCount(int dispatchThreadCount) {
        this._DispatchThreadCount=dispatchThreadCount;
    }
}