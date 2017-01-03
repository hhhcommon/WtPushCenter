package com.woting.push.config;

import com.woting.push.core.config.Config;

/**
 * 媒体包过期时间配置
 * @author wanghui
 */
public class MediaExpiredConfig implements Config {
    /**
     * 音频媒体包过期时间，多长时间没有发包，这个包就不发了
     */
    private int _AudioExpiredTime=1000;
    /**
     * 视频媒体包过期时间，多长时间没有发包，这个包就不发了
     */
    private int _VedioExpiredTime=1000;

    public int get_AudioExpiredTime() {
        return _AudioExpiredTime;
    }

    public void set_AudioExpiredTime(int _AudioExpiredTime) {
        this._AudioExpiredTime = _AudioExpiredTime;
    }

    public int get_VedioExpiredTime() {
        return _VedioExpiredTime;
    }

    public void set_VedioExpiredTime(int _VedioExpiredTime) {
        this._VedioExpiredTime = _VedioExpiredTime;
    }
}