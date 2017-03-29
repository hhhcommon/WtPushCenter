package com.woting.audioSNS.notify.monitor;

import java.util.TimerTask;

import com.woting.audioSNS.notify.mem.NotifyMemory;

public class CleanNotifyMsg  extends TimerTask {
    private NotifyMemory notifyMemory=NotifyMemory.getInstance();

    @Override
    public void run() {
        notifyMemory.cleanNotifyMsg();
    }
}