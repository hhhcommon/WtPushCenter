package com.woting.push.config;

/**
 * 每个tcp通道(socket)将监控线程的配置信息。
 * <pre>
 * 每个Socket连接都可以拥有自己的配置，但目前版本只实现所有Socket连接都采用相同的配置
 * </pre>
 * <br/>
 * 在设置类，所有字段前都有_
 * @author wanghui
 *
 */
public class TcpSocketConfig implements Config {
    //以下主控线程控制参数
    private long _ExpireTime=1000*240; //多长时间没有收到信息，若大于此时间没有获得信息，则系统认为Socket已经失效，将关闭相应的处理
    private long _MonitorDelay=1000; //主监控进程监控周期
    private long _TryDestoryAllCount=1000; //尝试销毁次数，大于此数量仍未达到销毁条件，则强制销毁
    //以下分线程控制参数
    private long _BeatDelay=1000; //多长时间发送一次心跳
    private long _RecieveErr_ContinueCount=3; //接收消息处理中，连续收到错误|异常消息的次数，若大于这个数量，则系统将认为此Socket为恶意连接，将关闭相应的处理
    private long _RecieveErr_SumCount=1000; //接收消息处理中，总共收到错误|异常消息的次数，若大于这个数量，则系统将认为此Socket为恶意连接，将关闭相应的处理
}