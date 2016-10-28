package com.woting.push.core.socket.oio;

import com.woting.push.config.SocketHandleConfig;
import com.woting.push.core.monitor.AbstractMoniterServer;

public class SocketHandler extends AbstractMoniterServer<SocketHandleConfig> {

    protected SocketHandler(SocketHandleConfig conf) {
        super(conf);
        // TODO Auto-generated constructor stub
    }

    @Override
    public boolean initServer() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void destroyServer() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean canContinue() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void oneProcess() throws Exception {
        // TODO Auto-generated method stub
        
    }

}
