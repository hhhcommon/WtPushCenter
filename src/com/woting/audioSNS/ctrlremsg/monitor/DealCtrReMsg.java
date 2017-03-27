package com.woting.audioSNS.ctrlremsg.monitor;

import com.woting.audioSNS.ctrlremsg.AffirmCtlConfig;
import com.woting.audioSNS.notify.mem.NotifyMemory;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class DealCtrReMsg extends AbstractLoopMoniter<AffirmCtlConfig> {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private NotifyMemory notifyMem=NotifyMemory.getInstance();

    public DealCtrReMsg(AffirmCtlConfig conf, int index) {
        super(conf);
        super.setName("通用回复处理线程"+index);
        this.setLoopDelay(5);
    }

    @Override
    public void oneProcess() throws Exception {
        Message m=globalMem.receiveMem.takeTypeMsg("0");
        if (m==null||!(m instanceof MsgNormal)) return;
        //删除需要的已处理消息
        globalMem.sendMem.delCtrAffirmMsg((MsgNormal)m, conf);
        notifyMem.matchCtrAffirmReMsg((MsgNormal)m);
    }

    @Override
    public boolean canContinue() {
        return true;
    }
}