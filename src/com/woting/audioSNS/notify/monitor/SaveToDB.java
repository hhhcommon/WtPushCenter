package com.woting.audioSNS.notify.monitor;

import java.util.Map;

import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.audioSNS.notify.mem.NotifyMemory;
import com.woting.audioSNS.notify.persis.NotifySaveService;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.ext.SpringShell;

public class SaveToDB extends AbstractLoopMoniter<NotifyMessageConfig> {
    private NotifyMemory notifyMem=NotifyMemory.getInstance();
    private NotifySaveService notifySaveService=null;

    public SaveToDB(NotifyMessageConfig conf) {
        super(conf);
        super.setName("数据库存储线程");
        notifySaveService=(NotifySaveService)SpringShell.getBean("notifySaveService");
        setLoopDelay(0);
    }

    @Override
    public void oneProcess() throws Exception {
        Map<String, Object> m=notifyMem.takeSaveDataQueue();
        if (m!=null&&m.get("TYPE")!=null) {
            if ((m.get("TYPE")+"").equals("insert")) {
                notifySaveService.insert(m);
            }
            if ((m.get("TYPE")+"").equals("update")) {
                notifySaveService.update(m);
            }
        }
    }

    @Override
    public boolean canContinue() {
        return true;
    }
}