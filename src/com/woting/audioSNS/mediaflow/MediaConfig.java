package com.woting.audioSNS.mediaflow;

import com.woting.push.core.config.Config;

/**
 * 媒体包过期时间配置
 * @author wanghui
 */
public class MediaConfig implements Config {
    /**
     * 音频包时长
     */
    private int _AudioPackT=40;
    /**
     * 音频包过期时间
     */
    private int _AudioPackExpiredTime=1000;
    /**
     * 音频过程过期类型，0无过期，必须都完成；1采用绝对时间；2采用过期周期；3采取联合方式，绝对时间和周期谁先到就过期
     */
    private int _AudioExpiredType=1;
    /**
     * 音频过程过期类型，过期周期数目，3个周期
     */
    private int _AudioExpiredTNum=3;
    /**
     * 音频过程过期时间，多长时间没有发包，这个包就不发了
     */
    private int _AudioExpiredTime=1000;

    /**
     * 视频包时长
     */
    private int _VedioPackT=40;
    /**
     * 视频包过期时间
     */
    private int _VedioPackExpiredTime=1000;
    /**
     * 视频过程过期类型，0无过期，必须都完成；1采用绝对时间；2采用过期周期；3采取联合方式，绝对时间和周期谁先到就过期
     */
    private int _VedioExpiredType=1;
    /**
     * 视频过程过期类型，过期周期数目，3个周期
     */
    private int _VedioExpiredTNum=3;
    /**
     * 视频过程过期时间，多长时间没有发包，这个包就不发了
     */
    private int _VedioExpiredTime=1000;

    public int get_AudioPackT() {
        return _AudioPackT;
    }
    public void set_AudioPackT(int _AudioPackT) {
        this._AudioPackT=_AudioPackT;
    }
    public int get_AudioPackExpiredTime() {
        return _AudioPackExpiredTime;
    }
    public void set_AudioPackExpiredTime(int _AudioPackExpiredTime) {
        this._AudioPackExpiredTime=_AudioPackExpiredTime;
    }
    public int get_AudioExpiredType() {
        return _AudioExpiredType;
    }
    public void set_AudioExpiredType(int _AudioExpiredType) {
        this._AudioExpiredType=_AudioExpiredType;
    }
    public int get_AudioExpiredTNum() {
        return _AudioExpiredTNum;
    }
    public void set_AudioExpiredTNum(int _AudioExpiredTNum) {
        this._AudioExpiredTNum=_AudioExpiredTNum;
    }
    public int get_AudioExpiredTime() {
        return _AudioExpiredTime;
    }
    public void set_AudioExpiredTime(int _AudioExpiredTime) {
        this._AudioExpiredTime=_AudioExpiredTime;
    }
    public int get_VedioPackT() {
        return _VedioPackT;
    }
    public void set_VedioPackT(int _VedioPackT) {
        this._VedioPackT=_VedioPackT;
    }
    public int get_VedioPackExpiredTime() {
        return _VedioPackExpiredTime;
    }
    public void set_VedioPackExpiredTime(int _VedioPackExpiredTime) {
        this._VedioPackExpiredTime=_VedioPackExpiredTime;
    }
    public int get_VedioExpiredType() {
        return _VedioExpiredType;
    }
    public void set_VedioExpiredType(int _VedioExpiredType) {
        this._VedioExpiredType=_VedioExpiredType;
    }
    public int get_VedioExpiredTNum() {
        return _VedioExpiredTNum;
    }
    public void set_VedioExpiredTNum(int _VedioExpiredTNum) {
        this._VedioExpiredTNum=_VedioExpiredTNum;
    }
    public int get_VedioExpiredTime() {
        return _VedioExpiredTime;
    }
    public void set_VedioExpiredTime(int _VedioExpiredTime) {
        this._VedioExpiredTime=_VedioExpiredTime;
    }

    public long getAudioExpiedTime() {
        if (_AudioExpiredType==0) return -1l;
        else if (_AudioExpiredType==2) return _AudioPackT*_AudioExpiredTNum;
        else if (_AudioExpiredType==3) return _AudioPackT*_AudioExpiredTNum>_AudioExpiredTime?_AudioExpiredTime:_AudioPackT*_AudioExpiredTNum;
        return _AudioExpiredTime;
    }

    public long getVedioExpiedTime() {
        if (_VedioExpiredType==0) return -1l;
        else if (_VedioExpiredType==2) return _AudioPackT*_AudioExpiredTNum;
        else if (_VedioExpiredType==3) return _AudioPackT*_AudioExpiredTNum>_AudioExpiredTime?_AudioExpiredTime:_AudioPackT*_AudioExpiredTNum;
        return _AudioExpiredTime;
    }
}