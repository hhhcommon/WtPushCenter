package com.woting.audioSNS.ctrlremsg.monitor;

import java.util.TimerTask;

import com.woting.push.core.mem.PushGlobalMemory;

public class CleanCtrAffirmMsg  extends TimerTask {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    @Override
    public void run() {
        globalMem.sendMem.cleanCtrAffirmMsg();
    }
}