package com.woting.audioSNS.notify.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class DealNotifyMsg extends AbstractLoopMoniter<NotifyMessageConfig> {
    private Logger logger=LoggerFactory.getLogger(DealNotifyMsg.class);

    protected DealNotifyMsg(NotifyMessageConfig nmc, int index) {
        super(nmc);
        super.setName("通知消息处理线程"+index);
    }

    @Override
    public void oneProcess() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean canContinue() {
        return true;
    }

}
