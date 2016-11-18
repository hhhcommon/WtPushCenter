package com.woting.audioSNS.intercom.monitor;

import com.woting.audioSNS.intercom.IntercomConfig;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class DealIntercom extends AbstractLoopMoniter<IntercomConfig> {

    protected DealIntercom(IntercomConfig conf) {
        super(conf);
    }

    @Override
    public void oneProcess() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean canContinue() {
        // TODO Auto-generated method stub
        return false;
    }

}
