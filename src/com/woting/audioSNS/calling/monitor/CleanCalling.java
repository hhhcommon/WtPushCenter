package com.woting.audioSNS.calling.monitor;

import java.util.Map;
import java.util.Timer;

import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class CleanCalling extends AbstractLoopMoniter<CallingConfig> {
    private long lastExcuteTime=System.currentTimeMillis();
    private CallingMemory callingMem=null;
    private PushGlobalMemory globalMem=null;

    public CleanCalling(CallingConfig conf) {
        super(conf);
        super.setName("电话数据清理");
        callingMem=CallingMemory.getInstance();
        globalMem=PushGlobalMemory.getInstance();
    }

    @Override
    public void oneProcess() throws Exception {
        long cur=System.currentTimeMillis();
        if (cur-lastExcuteTime>conf.get_CleanInternal()) {
            lastExcuteTime=cur;
            //清除内存
            Map<String, OneCall> delM=callingMem.getDelMap();
            if (delM!=null&&!delM.isEmpty()) {
                for (String k: delM.keySet()) {
                    String[] ks=k.split("::");
                    if (ks.length!=2) continue;
                    long delTime=Long.parseLong(ks[1]);
                    if (cur-delTime>conf.get_CleanDataExpire()) {//清除数据
                        OneCall oc=delM.remove(k);
                        if (oc!=null) {
                            oc.clear();//清除对象本身
                            callingMem.removeUserInCall(oc.getCallerId(), oc);
                            callingMem.removeUserInCall(oc.getCallederId(), oc);
                            //清除会话数据
                            globalMem.sendMem.cleanMsg4Call(oc.getCallerKey(), oc.getCallId());
                            globalMem.sendMem.cleanMsg4Call(oc.getCallederKey(), oc.getCallId());
                        }
                    }
                }
            }
        }
        try {
            sleep(conf.get_CleanInternal()-((System.currentTimeMillis()-cur)%conf.get_CleanInternal()));
        } catch(Exception e) {
        }
    }

    @Override
    public boolean canContinue() {
        return true;
    }
}