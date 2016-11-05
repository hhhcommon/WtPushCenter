package com.woting.push.core.monitor;

/**
 * 循环执行的监控服务 接口。
 * <pre>
 * 规范循环执行的监控服务类的实现方法，为循环执行的监控服务提供基础框架。
 * 本接口是可运行的，它继承了Runnable接口。
 * </pre>
 * @author wanghui
 */
public interface LoopMonitor extends Runnable {
    /**
     * 获得运行状态：0未启动，1正在启动，2启动成功；3准备停止；4停止
     * @return
     */
    public int getRUN_STATUS();

    /**
     * 初始化监控服务
     */
    public boolean initServer();

    /**
     * 停止监控服务。调用此过程后，通知监控服务要停止，但并不马上停止，还需要执行destroyServer。
     */
    public void stopServer();

    /**
     * 销毁服务，把服务用到的资源关闭掉。
     */
    public void destroyServer();

    /**
     * 循环执行的方法，每一监控周期所执行的过程。
     */
    public void oneProcess() throws Exception;

    /**
     * 是否已经停止
     */
    public boolean isStoped();
}