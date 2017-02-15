package com.woting.push.config;

import com.woting.push.core.config.Config;

/**
 * 服务端配置信息
 * @author wanghui
 */
public class PushConfig implements Config {

    private String _ServerType; //本服务器的类型
    private String _ServerName; //本服务器的名称
    public String get_ServerType() {
        return _ServerType;
    }
    public void set_ServerType(String _ServerType) {
        this._ServerType=_ServerType;
    }
    public String get_ServerName() {
        return _ServerName;
    }
    public void set_ServerName(String _ServerName) {
        this._ServerName=_ServerName;
    }

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