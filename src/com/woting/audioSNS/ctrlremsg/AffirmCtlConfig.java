package com.woting.audioSNS.ctrlremsg;

import com.woting.push.core.config.Config;

/**
 * 控制回复配置信息
 * @author wanghui
 */
public class AffirmCtlConfig implements Config {
    /**
     * 处理通用控制回复消息线程的个数，从原生队列中获取通用控制消息，并进行处理的线程的个数
     */
    private int _DealThreadCount=4;
    /**
     * 一般消息:上次发送后，间隔多长时间发送下一次
     */
    private int _N_InternalResend=1000;
    /**
     * 一般消息:重复发送的最大次数
     */
    private int _N_ExpireLimit=3;
    /**
     * 一般消息:过期时间，多长时间后未收到回执，这个消息被抛弃掉,-1是永远不过期
     */
    private int _N_ExpireTime=2000;
    /**
     * 一般消息:类型：0无重传机制；1采用次数限制方式；2采用过期时间方式；3采取联合方式，次数和时间谁先到就过期
     */
    private int _N_Type=0;

    public int get_N_InternalResend() {
        return _N_InternalResend;
    }
    public void set_N_InternalResend(int _N_InternalResend) {
        this._N_InternalResend = _N_InternalResend;
    }
    public int get_N_ExpireLimit() {
        return _N_ExpireLimit;
    }
    public void set_N_ExpireLimit(int _N_ExpireLimit) {
        this._N_ExpireLimit = _N_ExpireLimit;
    }
    public int get_N_ExpireTime() {
        return _N_ExpireTime;
    }
    public void set_N_ExpireTime(int _N_ExpireTime) {
        this._N_ExpireTime = _N_ExpireTime;
    }
    public int get_N_Type() {
        return _N_Type;
    }
    public void set_N_Type(int _N_Type) {
        this._N_Type = _N_Type;
    }
    /**
     * 清理线程时间间隔
     */
    private int _CleanInternal=60*1000;

    /**
     * 媒体消息:上次发送后，间隔多长时间发送下一次
     */
    private int _M_InternalResend=1000;
    /**
     * 媒体消息:重复发送的最大次数
     */
    private int _M_ExpireLimit=3;
    /**
     * 媒体消息:过期时间，多长时间后未收到回执，这个消息被抛弃掉,-1是永远不过期
     */
    private int _M_ExpireTime=2000;
    /**
     * 媒体消息:类型：0无重传机制；1采用次数限制方式；2采用过期时间方式；3采取联合方式，次数和时间谁先到就过期
     */
    private int _M_Type=0;

    public int get_DealThreadCount() {
        return _DealThreadCount;
    }
    public void set_DealThreadCount(int _DealThreadCount) {
        this._DealThreadCount=_DealThreadCount;
    }
    public int get_M_InternalResend() {
        return _M_InternalResend;
    }
    public void set_M_InternalResend(int _M_InternalResend) {
        this._M_InternalResend = _M_InternalResend;
    }
    public int get_M_ExpireLimit() {
        return _M_ExpireLimit;
    }
    public void set_M_ExpireLimit(int _M_ExpireLimit) {
        this._M_ExpireLimit = _M_ExpireLimit;
    }
    public int get_M_ExpireTime() {
        return _M_ExpireTime;
    }
    public void set_M_ExpireTime(int _M_ExpireTime) {
        this._M_ExpireTime = _M_ExpireTime;
    }
    public int get_M_Type() {
        return _M_Type;
    }
    public void set_M_Type(int _M_Type) {
        this._M_Type = _M_Type;
    }
    public int get_CleanInternal() {
        return _CleanInternal;
    }
    public void set_CleanInternal(int _CleanInternal) {
        this._CleanInternal = _CleanInternal;
    }
}