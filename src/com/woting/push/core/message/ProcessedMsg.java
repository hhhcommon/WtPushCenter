package com.woting.push.core.message;

import com.woting.push.core.message.MsgNormal;

/**
 * 已处理的消息对象
 * @author wanghui
 */
public class ProcessedMsg {
    private MsgNormal msg;//消息
    private long beginTime; //消息开始处理时间
    private long endTime;//消息处理结束时间
    private int status=0;//消息处理状态0正在处里;1处理成功;2被抛弃;3处理失败，由于信息不匹配;-1处理异常
    private String className; //处理消息的类名称
    private String errMsg;//若消息处理失败，这里存储失败的描述
    private Throwable ext;//若消息处理异常，这里存储引起异常的类实例  

    /**
     * 已处理消息的构造函数，这个构造函数限定：
     * 若要构造此类，必须要知道消息、开始处理时间和处理的类
     * @param msg
     * @param beginTime
     * @param className
     */
    public ProcessedMsg(MsgNormal msg, long beginTime, String className) {
        super();
        this.msg=msg;
        this.beginTime=beginTime;
        this.className=className;
        this.status=0;
    }
    public MsgNormal getMsg() {
        return msg;
    }
    public long getBeginTime() {
        return beginTime;
    }
    public long getEndTime() {
        return endTime;
    }
    public void setEndTime(long endTime) {
        this.endTime=endTime;
    }
    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status=status;
    }
    public String getClassName() {
        return className;
    }
    public String getErrMsg() {
        return errMsg;
    }
    public void setErrMsg(String errMsg) {
        this.errMsg=errMsg;
    }
    public Throwable getExt() {
        return ext;
    }
    public void setExt(Throwable ext) {
        this.ext=ext;
    }

    /**
     * 获取消息Id
     * @return 消息Id
     */
    public String getMsgId() {
        return this.msg.getMsgId();
    }
}