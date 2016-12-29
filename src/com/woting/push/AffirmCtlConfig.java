package com.woting.push;

import com.woting.push.config.Config;

/**
 * 控制回复配置信息
 * @author wanghui
 */
public class AffirmCtlConfig implements Config {
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

    /**
     * 一般消息:上次发送后，间隔多长时间发送下一次
     */
    private int _A_InternalResend=1000;
    /**
     * 一般消息:重复发送的最大次数
     */
    private int _A_ExpireLimit=3;
    /**
     * 一般消息:过期时间，多长时间后未收到回执，这个消息被抛弃掉,-1是永远不过期
     */
    private int _A_ExpireTime=2000;
    /**
     * 一般消息:类型：0无重传机制；1采用次数限制方式；2采用过期时间方式；3采取联合方式，次数和时间谁先到就过期
     */
    private int _A_Type=0;

}