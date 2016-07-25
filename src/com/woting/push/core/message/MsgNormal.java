package com.woting.push.core.message;

/**
 * 一般消息：既控制类消息
 * @author wanghui
 */
public class MsgNormal extends Message {
    private String msgId; //32位消息id
    private String reMsgId; //32位消息id
    private int bizType; //业务消息类型，根据此类型，框架会对消息进行分发
    private int cmdType; //命令类型
    private int command; //命令编号
    private int returnType; //返回值类型


    private int PCDType; //设备：设备类型
    private String userId; //设备：当前登录用户
    private String IMEI; //设备：设备串号

    private Object msgContent; //消息内容    

    public String getMsgId() {
        return msgId;
    }
    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getReMsgId() {
        return reMsgId;
    }
    public void setReMsgId(String reMsgId) {
        this.reMsgId = reMsgId;
    }

    public int getBizType() {
        return bizType;
    }
    public void setBizType(int bizType) {
        this.bizType = bizType;
    }

    public int getCmdType() {
        return cmdType;
    }
    public void setCmdType(int cmdType) {
        this.cmdType = cmdType;
    }


    public int getCommand() {
        return command;
    }
    public void setCommand(int command) {
        this.command = command;
    }

    public int getReturnType() {
        return returnType;
    }
    public void setReturnType(int returnType) {
        this.returnType = returnType;
    }

    public int getPCDType() {
        return PCDType;
    }
    public void setPCDType(int pCDType) {
        PCDType = pCDType;
    }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIMEI() {
        return IMEI;
    }
    public void setIMEI(String iMEI) {
        IMEI = iMEI;
    }

    public Object getMsgContent() {
        return msgContent;
    }
    public void setMsgContent(Object msgContent) {
        this.msgContent = msgContent;
    }

    @Override
    public void fromBytes(byte[] binaryMsg) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public byte[] toBytes() {
        // TODO Auto-generated method stub
        return null;
    }
}