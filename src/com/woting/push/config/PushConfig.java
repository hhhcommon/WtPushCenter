package com.woting.push.config;

public class PushConfig {
    private int controlTcpPort=15678;//推送服务控制信号的tcp端口号
    private int cleanInterval=5*60*1000;//检查清除会话的间隔时间，毫秒//5分钟
    private int dispatchThreadCount=4;//处理原生接收队列线程的个数

    public int getControlTcpPort() {
        return controlTcpPort;
    }
    protected void setControlTcpPort(int port) {
        this.controlTcpPort = port;
    }

    public int getCleanInterval() {
        return cleanInterval;
    }
    protected void setCleanInterval(int cleanInterval) {
        this.cleanInterval = cleanInterval;
    }

    public int getDispatchThreadCount() {
        return dispatchThreadCount;
    }
    protected void setDsispatchThreadCount(int dispatchThreadCount) {
        this.dispatchThreadCount = dispatchThreadCount;
    }
}