package com.woting.push.core.monitor;

/**
 * 监控服务的接口。
 * <pre>
 * 规范监控服务类的实现方法，为监控服务控制提供基础。
 * </pre>
 * @author wanghui
 */
public interface MonitorServer {
    public int getRUN_STATUS();
    public void stopServer();
    public void startServer();
    public void moniter();
}