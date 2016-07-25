package com.woting.push.config;

import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.woting.jsonconfig.JsonConfig;

public abstract class ConfigLoadUtils {
    private final static FelEngine fel=new FelEngineImpl();

    public static PushConfig getPushConfig(JsonConfig jc) {
        PushConfig pc=new PushConfig();
        pc.setControlTcpPort((int)fel.eval(jc.getString("push.ctlTcpPort")));
        pc.setDsispatchThreadCount((int)fel.eval(jc.getString("push.dispatchThread")));
        pc.setCleanInterval((int)fel.eval(jc.getString("push.cleanInterval")));
        return pc;
    }
}