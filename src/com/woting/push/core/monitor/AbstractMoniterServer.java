package com.woting.push.core.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.StringUtils;
import com.woting.push.config.Config;

/**
 * 监控服务的虚类
 * <pre>
 * 实现了停止服务的方法。
 * 为监控过程包了一个壳子，此shell在moniter中，并提供了oneProcess虚方法，作为监控中每一监控周期的执行过程。
 * 注意，任何Exception都不会中断Monitor过程。
 * </pre>
 * @author wanghui
 *
 */
public abstract class AbstractMoniterServer<C extends Config> extends Thread implements MonitorServer {
    private Logger logger=LoggerFactory.getLogger(AbstractMoniterServer.class);

    private static int _RUN_STATUS=0;//运行状态:0未启动，1正在启动，2启动成功；3准备停止；4停止
    protected C conf;

    protected AbstractMoniterServer(C conf) {
        this.conf=conf;
    }

    /**
     * 是否可以继续执行监控，这是一个条件函数，若条件为真，监控继续执行，否则，监控过程将停止
     * @return True，可以继续监控，False，监控将停止
     */
    public abstract boolean canContinue();

    /**
     * 每一监控周期所执行的过程。
     */
    public abstract void oneProcess() throws Exception;

    @Override
    public void setMoniterName(String mname) {
        super.setName(mname);
    }

    @Override
    public int getRUN_STATUS() {
        return _RUN_STATUS;
    }

    @Override
    public void stopServer() {
        _RUN_STATUS=3;
    }

    @Override
    public void moniter() {
        while(_RUN_STATUS==2&&canContinue()) {
            try {
                oneProcess();
            } catch(Exception e) {
                logger.error("服务[{}]监控过程异常：\n{}", this.getName(), StringUtils.getAllMessage(e));
            }
        }
        _RUN_STATUS=3;
    }

    /**
     * 启动监控线程
     */
    public void run() {
        _RUN_STATUS=1;
        boolean initOk=initServer();
        if (!initOk) {
            logger.info("服务[{}]初始化失败", this.getName());
        } else {
            _RUN_STATUS=2;
            try {
                moniter();
            } catch (Exception e) {
            } finally {
                _RUN_STATUS=3;
                destroyServer();
                _RUN_STATUS=4;
            }
        }
    }
}