package com.woting.push.core.message;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.spiritdata.framework.util.StringUtils;
import com.woting.push.core.message.content.MapContent;

/**
 * 一般消息：既控制类消息
 * @author wanghui
 */
public class MsgNormal extends Message {
    public final static int _MAXLENGTH=2048; //最大字节数

    private String msgId; //32位消息id
    private String reMsgId; //32位消息id
    private int bizType; //0应答;1组通话;2电话通话;4消息通知;15注册消息
    private int cmdType; //命令类型
    private int command; //命令编号
    private int returnType; //返回值类型

    private int PCDType; //设备：设备类型
    private String userId; //设备：当前登录用户
    private String IMEI; //设备：设备串号

    private MessageContent msgContent; //消息内容    


    public MsgNormal(byte[] msgBytes) {
        super();
        fromBytes(msgBytes);
    }
    public MsgNormal() {
    }
    
    public String getMsgId() {
        return msgId;
    }
    public void setMsgId(String msgId) {
        this.msgId=msgId;
    }

    public String getReMsgId() {
        return reMsgId;
    }
    public void setReMsgId(String reMsgId) {
        this.reMsgId=reMsgId;
    }

    public int getBizType() {
        return bizType;
    }
    public void setBizType(int bizType) {
        this.bizType=bizType;
    }

    public int getCmdType() {
        return cmdType;
    }
    public void setCmdType(int cmdType) {
        this.cmdType=cmdType;
    }


    public int getCommand() {
        return command;
    }
    public void setCommand(int command) {
        this.command=command;
    }

    public int getReturnType() {
        return returnType;
    }
    public void setReturnType(int returnType) {
        this.returnType=returnType;
    }

    public int getPCDType() {
        return PCDType;
    }
    public void setPCDType(int pCDType) {
        PCDType=pCDType;
    }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId=userId;
    }

    public String getIMEI() {
        return IMEI;
    }
    public void setIMEI(String iMEI) {
        IMEI=iMEI;
    }

    public MessageContent getMsgContent() {
        return msgContent;
    }
    public void setMsgContent(MessageContent msgContent) {
        this.msgContent=msgContent;
    }

    @Override
    public void fromBytes(byte[] binaryMsg) {
        if (!MessageUtils.parse_EndFlagOk(binaryMsg)) throw new RuntimeException("消息未正确结束！");

        String _tempStr=null;
        String[] _sa=null;
        int _offset=0;

        byte f1=binaryMsg[_offset++];
        this.setMsgType(((f1&0x80)==0x80)?1:0);
        this.setAffirm(((f1&0x08)==0x08)?1:0);

        byte[] _tempBytes=Arrays.copyOfRange(binaryMsg, _offset, _offset+8);//ByteBuffer.wrap(binaryMsg, _offset, 8).array();
        this.setSendTime(ByteConvert.bytes2long(_tempBytes));
        _offset+=8;

        f1=binaryMsg[_offset++];
        this.setBizType(f1>>4);
        this.setCmdType(f1&0x0F);

        if (isAck()) {
            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new RuntimeException("消息字符串异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new RuntimeException("消息字符串异常！");
            _offset=Integer.parseInt(_sa[0]);
            this.setReMsgId(_sa[1]);
        } else {
            if (msgType==1) this.setReturnType(binaryMsg[_offset++]);

            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new RuntimeException("消息字符串异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new RuntimeException("消息字符串异常！");
            _offset=Integer.parseInt(_sa[0]);
            this.setMsgId(_sa[1]);

            if (msgType==1) {
                try {
                    _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                } catch (UnsupportedEncodingException e) {
                }
                _sa=_tempStr.split("::");
                if (_sa.length!=2) throw new RuntimeException("消息字符串异常！");
                if (Integer.parseInt(_sa[0])==-1) throw new RuntimeException("消息字符串异常！");
                _offset=Integer.parseInt(_sa[0]);
                this.setReMsgId(_sa[1]);
            }
            
        }

        f1=binaryMsg[_offset++];
        if ((f1&0xf0)==0x10) this.setFromType(1);
        else if ((f1&0xf0)==0x00) this.setFromType(0);
        else throw new RuntimeException("消息来源异常！");
        if ((f1&0x0f)==0x01) this.setToType(1);
        else if ((f1&0x0f)==0x00) this.setToType(0);
        else throw new RuntimeException("消息目标异常！");

        if (!isAck()) {
            if (fromType==0) {
                f1=binaryMsg[_offset++];
                this.setPCDType(f1);
                f1=binaryMsg[_offset];
                if (f1==0x00) {
                    _offset++;
                    this.setUserId(null);
                } else {
                    try {
                        _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 12, null);
                    } catch (UnsupportedEncodingException e) {
                    }
                    _sa=_tempStr.split("::");
                    if (_sa.length!=2) throw new RuntimeException("消息字节串异常！");
                    if (Integer.parseInt(_sa[0])==-1) throw new RuntimeException("消息字节串异常！");
                    _offset=Integer.parseInt(_sa[0]);
                    this.setUserId(_sa[1]);
                }
                try {
                    _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                } catch (UnsupportedEncodingException e) {
                }
                _sa=_tempStr.split("::");
                if (_sa.length!=2) throw new RuntimeException("消息字节串异常！");
                if (Integer.parseInt(_sa[0])==-1) throw new RuntimeException("消息字节串异常！");
                _offset=Integer.parseInt(_sa[0]);
                this.setIMEI(_sa[1]);
            }
        }

        if (!(binaryMsg[_offset]==END_HEAD[1]&&binaryMsg[_offset+1]==END_HEAD[0])) {
            throw new RuntimeException("消息字节串异常！");
        }
        _offset+=2;

        byte[] binaryCnt=Arrays.copyOfRange(binaryMsg, _offset, binaryMsg.length-2);
        MapContent mc=new MapContent();
        mc.fromBytes(binaryCnt);
        this.setMsgContent(mc);
    }

    @Override
    public byte[] toBytes() {
        int _offset=0;
        byte[] ret=new byte[_MAXLENGTH];
        byte zeroByte=0;

        if (msgType==1) zeroByte|=0x80;
        if (affirm==1) zeroByte|=0x08;
        ret[_offset++]=zeroByte;

        byte[] _tempBytes=ByteConvert.long2bytes(sendTime);
        int i=0;
        for (; i<8; i++) ret[_offset++]=_tempBytes[i];

        zeroByte=0;
        zeroByte|=(((byte)bizType)<<4);
        if (bizType!=0&&bizType!=15) zeroByte|=((((byte)cmdType)<<4)>>4);
        ret[_offset++]=zeroByte;

        if (bizType!=0&&bizType!=15) ret[_offset++]=(byte)command;

        if (msgType==1) ret[_offset++]=(byte)returnType;

        if (!isAck()) {
            if (StringUtils.isNullOrEmptyOrSpace(msgId)) throw new RuntimeException("消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, msgId, null);
            } catch (UnsupportedEncodingException e) {
            }
            if (msgType==1) {
                if (StringUtils.isNullOrEmptyOrSpace(reMsgId)) throw new RuntimeException("回复消息Id为空");
                try {
                    _offset=MessageUtils.set_String(ret, _offset, 32, reMsgId, null);
                } catch (UnsupportedEncodingException e) {
                }
            }
        } else {
            if (StringUtils.isNullOrEmptyOrSpace(reMsgId)) throw new RuntimeException("回复消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, reMsgId, null);
            } catch (UnsupportedEncodingException e) {
            }
        }

        zeroByte=0;
        zeroByte|=(fromType==1?0x00:0x10);
        zeroByte|=(toType==1?0x00:0x01);
        ret[_offset++]=zeroByte;

        if (!isAck()) {
            if (fromType==0) {//只有从设备发往服务器的消息才有如下信息
                if (StringUtils.isNullOrEmptyOrSpace(IMEI)) throw new RuntimeException("IMEI为空");
                ret[_offset++]=(byte)this.PCDType;
                if (StringUtils.isNullOrEmptyOrSpace(userId)) ret[_offset++]=0x00;
                else {
                    try {
                        _offset=MessageUtils.set_String(ret, _offset, 12, userId, null);
                    } catch (UnsupportedEncodingException e) {
                    }
                }
                try {
                    _offset=MessageUtils.set_String(ret, _offset, 32, IMEI, null);
                } catch (UnsupportedEncodingException e) {
                }
            }
        }

        ret[_offset++]=END_HEAD[1];
        ret[_offset++]=END_HEAD[0];

        //组装消息体
        if (!isAck()) {
            if (msgContent!=null) {
                _tempBytes=msgContent.toBytes();
                if (_tempBytes!=null&&_tempBytes.length>0) {
                    for (i=0; i<_tempBytes.length; i++) ret[_offset++]=_tempBytes[i];
                }
            }
        }
        //消息结束标志
        ret[_offset++]=END_MSG[1];
        ret[_offset]=END_MSG[0];
        byte[] _ret=Arrays.copyOfRange(ret, 0, _offset);
        return _ret;
    }

    //以下为消息类型判断函数，判断本条消息的类型
    /**
     * 判断是否是应答消息
     */
    public boolean isAck() {
        return affirm==0&&msgType==1&&bizType==0;
    }
}