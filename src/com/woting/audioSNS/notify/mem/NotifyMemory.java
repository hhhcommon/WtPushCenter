package com.woting.audioSNS.notify.mem;

public class NotifyMemory {
    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static NotifyMemory instance=new NotifyMemory();
    }
    public static NotifyMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

}
