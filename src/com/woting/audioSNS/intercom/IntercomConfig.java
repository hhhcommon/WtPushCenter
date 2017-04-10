package com.woting.audioSNS.intercom;

import com.woting.push.core.config.Config;

public class IntercomConfig implements Config {
    /**
     * 处理原生接收队列线程的个数，从原生队列中获取与电话处理的相关消息，并进行处理的线程的个数。此线程包括分发和创建处理线程
     */
    private int _DealThreadCount=2;
    /**
     * 控制回复的类型
     */
    private int _CtrAffirmType=0;
    /**
     * 检查是否在线的过期时间，毫秒
     */
    private long _ExpireTime=120*60*1000;
    /**
     * Speaker过期时间，毫秒
     */
    private long _ExpireSpeakerTime=5*1000;

    public int get_DealThreadCount() {
        return _DealThreadCount;
    }
    public void set_DealThreadCount(int _DealThreadCount) {
        this._DealThreadCount=_DealThreadCount;
    }
    public long get_ExpireTime() {
        return _ExpireTime;
    }
    public void set_ExpireTime(long _ExpireTime) {
        this._ExpireTime=_ExpireTime;
    }
    public long get_ExpireSpeakerTime() {
        return _ExpireSpeakerTime;
    }
    public void set_ExpireSpeakerTime(long _ExpireSpeakerTime) {
        this._ExpireSpeakerTime=_ExpireSpeakerTime;
    }
    public int get_CtrAffirmType() {
        return _CtrAffirmType;
    }
    public void set_CtrAffirmType(int _CtrAffirmType) {
        this._CtrAffirmType = _CtrAffirmType;
    }
}