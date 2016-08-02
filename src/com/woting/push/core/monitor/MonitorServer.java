package com.woting.push.core.monitor;

/**
 * 监控服务的接口。
 * <pre>
 * 规范监控服务类的实现方法，为监控服务控制提供基础。
 * 本接口是可运行的，它继承了Runnable接口。
 * </pre>
 * @author wanghui
 */
public interface MonitorServer extends Runnable {
    /**
     * 设置监控的名称，此名称会作为运行线程的名称，请在线程启动前设置监控名称
     * @param mname 监控名称
     */
    public void setMoniterName(String mname);

    /**
     * 获得运行状态：0未启动，1正在启动，2启动成功；3准备停止；4停止
     * @return
     */
    public int getRUN_STATUS();

    /**
     * 初始化监控服务
     */
    public void initServer();

    /**
     * 停止监控服务。调用此过程后，通知监控服务要停止，但并不马上停止，还需要执行destroyServer。
     */
    public void stopServer();

    /**
     * 销毁服务，把服务用到的资源关闭掉。
     */
    public void destroyServer();

    /**
     * 监控方法，执行listen的方法
     * <pre>
     * 注意这个监控方法必须是循环形式，否则moniter过程执行完，会直接调用服务销毁过程，在此情况下，服务仅执行一次monitor过程
     * </pre>
     */
    public void moniter();
}