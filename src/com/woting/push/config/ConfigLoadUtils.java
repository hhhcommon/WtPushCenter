package com.woting.push.config;

import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.spiritdata.framework.jsonconf.JsonConfig;

public abstract class ConfigLoadUtils {
    private final static FelEngine fel=new FelEngineImpl();

    public static PushConfig getPushConfig(JsonConfig jc) {
        PushConfig pc=new PushConfig();
        pc.set_ControlTcpPort((int)fel.eval(jc.getString("push.ctlTcpPort")));
        pc.set_DsispatchThreadCount((int)fel.eval(jc.getString("push.dispatchThread")));
        pc.set_CleanInterval((int)fel.eval(jc.getString("push.cleanInterval")));
        return pc;
    }
}