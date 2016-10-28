package com.woting.push.config;

import com.greenpineyu.fel.FelEngine;
import com.greenpineyu.fel.FelEngineImpl;
import com.spiritdata.framework.jsonconf.JsonConfig;

public abstract class ConfigLoadUtils {
    private final static FelEngine fel=new FelEngineImpl();

    public static PushConfig getPushConfig(JsonConfig jc) {
        PushConfig pc=new PushConfig();
        try {
            pc.set_ControlTcpPort((int)fel.eval(jc.getString("pushserver.ctlTcpPort")));
        } catch(Exception e) {}
        try {
            pc.set_DsispatchThreadCount((int)fel.eval(jc.getString("pushserver.dispatchThread")));
        } catch(Exception e) {}
        try {
            pc.set_CleanInterval((int)fel.eval(jc.getString("pushserver.cleanInterval")));
        } catch(Exception e) {}
        return pc;
    }

    public static SocketHandleConfig getSocketHandleConfig(JsonConfig jc) {
        SocketHandleConfig shc=new SocketHandleConfig();
        try {
            shc.set_ExpireTime((int)fel.eval(jc.getString("sockethandle.expireTime")));
        } catch(Exception e) {}
        try {
            shc.set_MonitorDelay((int)fel.eval(jc.getString("sockethandle.monitorDelay")));
        } catch(Exception e) {}
        try {
            shc.set_TryDestoryAllCount((int)fel.eval(jc.getString("sockethandle.tryDestoryAllCount")));
        } catch(Exception e) {}
        try {
            shc.set_BeatDelay((int)fel.eval(jc.getString("sockethandle.beatDelay")));
        } catch(Exception e) {}
        try {
            shc.set_RecieveErr_ContinueCount((int)fel.eval(jc.getString("sockethandle.recieveErr_ContinueCount")));
        } catch(Exception e) {}
        try {
            shc.set_RecieveErr_SumCount((int)fel.eval(jc.getString("sockethandle.recieveErr_SumCount")));
        } catch(Exception e) {}
        return shc;
    }
}