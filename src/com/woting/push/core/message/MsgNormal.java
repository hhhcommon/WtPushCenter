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
    private static final long serialVersionUID=-5354794282645342159L;

    private String msgId; //32位消息id
    private String reMsgId; //32位消息id
    private int bizType; //0应答;1组通话;2电话通话;4消息通知;15注册消息
    private int cmdType; //命令类型
    private int command; //命令编号
    private int returnType; //返回值类型

    private int PCDType; //设备：设备类型:1手机;2设备;3网站;0服务器
    private String userId; //设备：当前登录用户
    private String IMEI; //设备：设备串号

    private MessageContent msgContent; //消息内容    

    public MsgNormal(byte[] msgBytes) throws Exception {
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
    public void fromBytes(byte[] binaryMsg) throws Exception {
        if (MessageUtils.decideMsg(binaryMsg)!=0) throw new Exception("消息类型错误！");

        int _offset=2;//一、头
        String _tempStr=null;
        String[] _sa=null;

        //二、类型
        byte f1=binaryMsg[_offset++];
        setMsgType(((f1&0x80)==0x80)?1:0);
        setAffirm(((f1&0x08)==0x08)?1:((f1&0x0A)==0x0A)?3:((f1&0x02)==0x02)?2:0);
        //三、时间
        byte[] _tempBytes=Arrays.copyOfRange(binaryMsg, _offset, _offset+8);
        setSendTime(ByteConvert.bytes2long(_tempBytes));
        _offset+=8;
        //四、命令
        f1=binaryMsg[_offset++];
        setBizType((f1>>4)&0x0F);
        setCmdType(f1&0x0F);
        _tempBytes=new byte[4];
        if (bizType!=0&&bizType!=15) {
            _tempBytes[0]=binaryMsg[_offset++];
            setCommand(ByteConvert.bytes2int(_tempBytes));
        }
        //五、回复
        if (bizType!=0&&msgType==1) {
            _tempBytes[0]=binaryMsg[_offset++];
            setReturnType(ByteConvert.bytes2int(_tempBytes));
        }
        //六、消息Id
        if (!isAck()) {
            if (msgType==0) {
                try {
                    _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                } catch (UnsupportedEncodingException e) {
                }
                _sa=_tempStr.split("::");
                if (_sa.length!=2) throw new Exception("消息字节数组异常！");
                if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
                _offset=Integer.parseInt(_sa[0]);
                setMsgId(_sa[1]);
            } else {
                if (bizType!=15) {
                    try {
                        _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                    } catch (UnsupportedEncodingException e) {
                    }
                    _sa=_tempStr.split("::");
                    if (_sa.length!=2) throw new Exception("消息字节数组异常！");
                    if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
                    _offset=Integer.parseInt(_sa[0]);
                    setMsgId(_sa[1]);
                }
                try {
                    _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                } catch (UnsupportedEncodingException e) {
                }
                _sa=_tempStr.split("::");
                if (_sa.length!=2) throw new Exception("消息字节数组异常！");
                if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
                _offset=Integer.parseInt(_sa[0]);
                setReMsgId(_sa[1]);
            }
        } else {
            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new Exception("消息字节数组异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
            _offset=Integer.parseInt(_sa[0]);
            setReMsgId(_sa[1]);
        }
        //七，邮递类型
        f1=binaryMsg[_offset++];
        if ((f1&0xf0)==0x10) setFromType(1);
        else if ((f1&0xf0)==0x00) setFromType(0);
        else throw new Exception("消息来源异常！");
        if ((f1&0x0f)==0x01) setToType(1);
        else if ((f1&0x0f)==0x00) setToType(0);
        else throw new Exception("消息目标异常！");
        if (!isAck()) {
            //八、用户类型
            if (fromType==0||(fromType==1&&bizType==15)||(fromType==1&&toType==1&&(bizType==4||bizType==8))) {
                f1=binaryMsg[_offset++];
                setPCDType(f1);
                f1=binaryMsg[_offset];
                if (f1==0x00) {
                    _offset++;
                    setUserId(null);
                } else {
                    try {
                        _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 12, null);
                    } catch (UnsupportedEncodingException e) {
                    }
                    _sa=_tempStr.split("::");
                    if (_sa.length!=2) throw new Exception("消息字节串异常！");
                    if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节串异常！");
                    _offset=Integer.parseInt(_sa[0]);
                    setUserId(_sa[1]);
                }
                try {
                    _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                } catch (UnsupportedEncodingException e) {
                }
                _sa=_tempStr.split("::");
                if (_sa.length!=2) throw new Exception("消息字节串异常！");
                if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节串异常！");
                _offset=Integer.parseInt(_sa[0]);
                setIMEI(_sa[1]);
            }
            //九、实体数据
            if (bizType!=15) {
                if (!(binaryMsg[_offset]==END_HEAD[0]&&binaryMsg[_offset+1]==END_HEAD[1])) throw new Exception("消息字节串异常！");
                _offset+=4;
                short _dataLen=(short)(((binaryMsg[_offset-1]<<8)|binaryMsg[_offset-2]&0xff));
                if (_dataLen>0) {
                    byte[] binaryCnt=Arrays.copyOfRange(binaryMsg, _offset, _offset+_dataLen);
                    MapContent mc=new MapContent();
                    mc.fromBytes(binaryCnt);
                    setMsgContent(mc);
                }
            }
        } else {
            if (bizType==15) {
                //八、用户类型
                if (fromType==0||(fromType==1&&bizType==15)) {
                    f1=binaryMsg[_offset++];
                    setPCDType(f1);
                    f1=binaryMsg[_offset];
                    if (f1==0x00) {
                        _offset++;
                        setUserId(null);
                    } else {
                        try {
                            _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 12, null);
                        } catch (UnsupportedEncodingException e) {
                        }
                        _sa=_tempStr.split("::");
                        if (_sa.length!=2) throw new Exception("消息字节串异常！");
                        if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节串异常！");
                        _offset=Integer.parseInt(_sa[0]);
                        setUserId(_sa[1]);
                    }
                    try {
                        _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
                    } catch (UnsupportedEncodingException e) {
                    }
                    _sa=_tempStr.split("::");
                    if (_sa.length!=2) throw new Exception("消息字节串异常！");
                    if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节串异常！");
                    _offset=Integer.parseInt(_sa[0]);
                    setIMEI(_sa[1]);
                }
            }
        }
    }

    @Override
    public byte[] toBytes() throws Exception {//1服务器；0设备
        if (bizType==15&&msgType==1&&affirm==0&&fromType!=1) throw new Exception("注册回复消息格式错误!");;
        int _offset=0;
        byte[] ret=new byte[_MAXLENGTH];
        byte zeroByte=0;
        //一、头
        ret[_offset++]=BEGIN_CTL[0];
        ret[_offset++]=BEGIN_CTL[1];
        //二、类型
        if (msgType==1) zeroByte|=0x80;
        if (affirm==1) zeroByte|=0x08;
        else if (affirm==2) zeroByte|=0x02;
        else if (affirm==3) zeroByte|=0x0A;
        ret[_offset++]=zeroByte;
        //三、时间
        byte[] _tempBytes=ByteConvert.long2bytes(sendTime);
        int i=0;
        for (; i<8; i++) ret[_offset++]=_tempBytes[i];
        //四、命令
        zeroByte=0;
        zeroByte|=(((byte)bizType)<<4);
        zeroByte|=((((byte)cmdType)<<4)>>4);
        ret[_offset++]=zeroByte;
        if (bizType!=0&&bizType!=15) ret[_offset++]=(byte)command;
        //五、回复
        if (bizType!=0&&msgType==1) ret[_offset++]=(byte)returnType;
        //六、消息Id
        if (!isAck()) {
            if (msgType==0) {
                if (StringUtils.isNullOrEmptyOrSpace(msgId)) throw new Exception("消息Id为空");
                try {
                    _offset=MessageUtils.set_String(ret, _offset, 32, msgId, null);
                } catch (UnsupportedEncodingException e) {
                }
            } else {
                if (StringUtils.isNullOrEmptyOrSpace(reMsgId)) throw new Exception("回复消息Id为空");
                if (bizType!=15) {
                    if (StringUtils.isNullOrEmptyOrSpace(msgId)) throw new Exception("消息Id为空");
                    try {
                        _offset=MessageUtils.set_String(ret, _offset, 32, msgId, null);
                    } catch (UnsupportedEncodingException e) {
                    }
                }
                try {
                    _offset=MessageUtils.set_String(ret, _offset, 32, reMsgId, null);
                } catch (UnsupportedEncodingException e) {
                }
            }
        } else {
            if (StringUtils.isNullOrEmptyOrSpace(reMsgId)) throw new Exception("消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, reMsgId, null);
            } catch (UnsupportedEncodingException e) {
            }
        }
        //七，邮递类型
        zeroByte=0;
        zeroByte|=(fromType==1?0x10:0x00);
        zeroByte|=(toType==1?0x01:0x00);
        ret[_offset++]=zeroByte;

        if (!isAck()) {
            //八、用户类型
            if (StringUtils.isNullOrEmptyOrSpace(IMEI)&&fromType==0) throw new Exception("IMEI为空");
            if (!StringUtils.isNullOrEmptyOrSpace(IMEI)) {
                ret[_offset++]=(byte)PCDType;
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
            //九、实体数据
            if (bizType!=15) {
                ret[_offset++]=END_HEAD[1];
                ret[_offset++]=END_HEAD[0];
                if (msgContent!=null) {
                    _tempBytes=msgContent.toBytes();
                    short len=(short)(_tempBytes==null?0:_tempBytes.length);
                    ret[_offset++]=(byte)(len>>0);
                    ret[_offset++]=(byte)(len>>8);
                    //组装消息体
                    if (_tempBytes!=null&&_tempBytes.length>0) {
                        for (i=0; i<_tempBytes.length; i++) ret[_offset++]=_tempBytes[i];
                    }
                } else {
                    ret[_offset++]=(byte)(0>>0);
                    ret[_offset++]=(byte)(0>>8);
                }
            }
        } else {
            if (bizType==15) {
                //八、用户类型
                if (StringUtils.isNullOrEmptyOrSpace(IMEI)&&fromType==0) throw new Exception("IMEI为空");
                if (!StringUtils.isNullOrEmptyOrSpace(IMEI)) {
                    ret[_offset++]=(byte)PCDType;
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
        }

        byte[] _ret=Arrays.copyOfRange(ret, 0, _offset);
        return _ret;
    }

    //以下为消息类型判断函数，判断本条消息的类型
    /**
     * 判断是否是应答消息
     */
    public boolean isAck() {
        return affirm==0&&msgType==1&&(bizType==0||bizType==15);
    }

    @Override
    public boolean equals(Message msg) {
        if (!(msg instanceof MsgNormal)) return false;
        if (msgId==null) return false;

        if (!equalsMsg(msg)) return false;
        MsgNormal _m=(MsgNormal)msg;
        if (!msgId.equals(_m.msgId)) return false;
        if (reMsgId!=null) {
            if (!reMsgId.equals(_m.reMsgId)) return false;
        } else if (_m.reMsgId!=null) return false;
        if (bizType!=_m.bizType) return false;
        if (cmdType!=_m.cmdType) return false;
        if (command!=_m.command) return false;
        if (!msgId.equals(_m.msgId)) return false;
        if (returnType!=_m.returnType) return false;

        if (IMEI!=null) {
            if (!IMEI.equals(_m.IMEI)) return false;
        } else if (_m.IMEI!=null) return false;
        if (userId!=null) {
            if (!userId.equals(_m.userId)) return false;
        } else if (_m.userId!=null) return false;
        if (PCDType!=_m.PCDType) return false;

        if (msgContent==null&&_m.msgContent==null) return true;
        if (msgContent!=null&&_m.msgContent==null) return false;
        if (msgContent==null&&_m.msgContent!=null) return false;

        return msgContent.equals(_m.msgContent);
    }
}