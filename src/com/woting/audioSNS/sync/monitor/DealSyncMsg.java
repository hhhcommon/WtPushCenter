package com.woting.audioSNS.sync.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.audioSNS.sync.SyncMessageConfig;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class DealSyncMsg extends AbstractLoopMoniter<SyncMessageConfig> {
    private Logger logger=LoggerFactory.getLogger(DealSyncMsg.class);

    protected DealSyncMsg(SyncMessageConfig smc, int index) {
        super(smc);
        super.setName("同步消息处理线程"+index);
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
