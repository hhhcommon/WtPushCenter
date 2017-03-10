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
    private int bizType; //0应答;1组通话;2电话通话;4消息通知;8同步消息;15注册消息
    private int cmdType; //命令类型
    private int command; //命令编号
    private int returnType; //返回值类型

    private String userId; //设备：当前登录用户；服务器：服务器类型
    private String deviceId; //设备：设备串号；服务器：服务器Id

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
        return fromType;
    }
    public int setPCDType(int PCDType) {
        return fromType=PCDType;
    }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId=userId;
    }

    public String getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(String deviceId) {
        this.deviceId=deviceId;
    }

    public MessageContent getMsgContent() {
        return msgContent;
    }
    public void setMsgContent(MessageContent msgContent) {
        this.msgContent=msgContent;
    }

    @Override
    public void fromBytes(byte[] binaryMsg) throws Exception {
        if (MessageUtils.decideMsg(binaryMsg)!=0) throw new Exception("非信令包格式错误！");
        if (MessageUtils.endOK(binaryMsg)!=1) throw new Exception("消息未正常结束！");

        int _offset=2;//一、头
        String _tempStr=null;
        String[] _sa=null;

        //二、类型
        byte f1=binaryMsg[_offset++];
        setMsgType(((f1&0xF0)==0x80)?1:0);
        setAffirm(((f1&0x0F)==0x08)?1:((f1&0x0F)==0x0A)?3:((f1&0x0F)==0x02)?2:0);
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
        int _midType=0; //1=只有MsgId；2=只有ReMsgId；3=先MsgId，再ReMsgId
        if (bizType!=0&&bizType!=15) {
            _midType=isAck()?3:1;
        } else {
            if (bizType==0) _midType=2;
            if (bizType==15) _midType=isAck()?2:1;
        }
        if (_midType==1) {
            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new Exception("消息字节数组异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
            _offset=Integer.parseInt(_sa[0]);
            setMsgId(_sa[1]);
        } else if (_midType==2) {
            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new Exception("消息字节数组异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
            _offset=Integer.parseInt(_sa[0]);
            setReMsgId(_sa[1]);
        } else if (_midType==3) {
            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new Exception("消息字节数组异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
            _offset=Integer.parseInt(_sa[0]);
            setMsgId(_sa[1]);
            try {
                _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
            } catch (UnsupportedEncodingException e) {
            }
            _sa=_tempStr.split("::");
            if (_sa.length!=2) throw new Exception("消息字节数组异常！");
            if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节数组异常！");
            _offset=Integer.parseInt(_sa[0]);
            setReMsgId(_sa[1]);
        } else {
            throw new Exception("消息字节数组异常！");
        }
        //七、邮递类型
        f1=binaryMsg[_offset++];
        setFromType(f1>>4);
        setToType(f1&0x0F);
        //八、设备类型、用户和设备Id
        //8.1用户Id
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
        //8.2设备Id
        try {
            _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 32, null);
        } catch (UnsupportedEncodingException e) {
        }
        _sa=_tempStr.split("::");
        if (_sa.length!=2) throw new Exception("消息字节串异常！");
        if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息字节串异常！");
        _offset=Integer.parseInt(_sa[0]);
        setDeviceId(_sa[1]);
        //九、实体数据
        if (bizType!=15&&bizType!=0) {
//            if (!(binaryMsg[_offset]==END_HEAD[0]&&binaryMsg[_offset+1]==END_HEAD[1])) throw new Exception("消息字节串异常！");
            _offset+=2;
            short _dataLen=(short)(((binaryMsg[_offset-1]<<8)|binaryMsg[_offset-2]&0xff));
            if (_dataLen>0) {
                byte[] binaryCnt=Arrays.copyOfRange(binaryMsg, _offset, _offset+_dataLen);
                MapContent mc=new MapContent();
                mc.fromBytes(binaryCnt);
                setMsgContent(mc);
            }
        }
    }

    @Override
    public byte[] toBytes() throws Exception {
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
        int _midType=0; //1=只有MsgId；2=只有ReMsgId；3=先MsgId，再ReMsgId
        if (bizType!=0&&bizType!=15) {
            _midType=isAck()?3:1;
        } else {
            if (bizType==0) _midType=2;
            if (bizType==15) _midType=isAck()?2:1;
        }
        if (_midType==1) {
            if (StringUtils.isNullOrEmptyOrSpace(msgId)) throw new Exception("消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, msgId, null);
            } catch (UnsupportedEncodingException e) {
            }
        } else if (_midType==2) {
            if (StringUtils.isNullOrEmptyOrSpace(reMsgId)) throw new Exception("回复消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, reMsgId, null);
            } catch (UnsupportedEncodingException e) {
            }
        } else if (_midType==3) {
            if (StringUtils.isNullOrEmptyOrSpace(msgId)) throw new Exception("消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, msgId, null);
            } catch (UnsupportedEncodingException e) {
            }
            if (StringUtils.isNullOrEmptyOrSpace(reMsgId)) throw new Exception("回复消息Id为空");
            try {
                _offset=MessageUtils.set_String(ret, _offset, 32, reMsgId, null);
            } catch (UnsupportedEncodingException e) {
            }
        } else {
            throw new Exception("消息格式异常！");
        }
        //七、邮递类型
        zeroByte=0;
        zeroByte|=(((byte)fromType)<<4);
        zeroByte|=((((byte)toType)<<4)>>4);
        ret[_offset++]=zeroByte;
        //八、设备类型、用户和设备Id
        //8.1用户Id
        if (StringUtils.isNullOrEmptyOrSpace(userId)) ret[_offset++]=0x00;
        else {
            try {
                _offset=MessageUtils.set_String(ret, _offset, 12, userId, null);
            } catch (UnsupportedEncodingException e) {
            }
        }
        if (StringUtils.isNullOrEmptyOrSpace(deviceId)) throw new Exception("设备类型为空");
        //8.2设备Id
        try {
            _offset=MessageUtils.set_String(ret, _offset, 32, deviceId, null);
        } catch (UnsupportedEncodingException e) {
        }
        //九、实体数据
        if (bizType!=15&&bizType!=0) {
//            ret[_offset++]=END_HEAD[1];
//            ret[_offset++]=END_HEAD[0];
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

        ret[_offset++]=Message.END_MSG[0];
        ret[_offset++]=Message.END_MSG[1];
        byte[] _ret=Arrays.copyOfRange(ret, 0, _offset);
        return _ret;
    }

    //以下为消息类型判断函数，判断本条消息的类型
    /**
     * 判断是否是应答消息
     */
    @Override
    public boolean isAck() {
        return msgType==1;
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

        if (deviceId!=null) {
            if (!deviceId.equals(_m.deviceId)) return false;
        } else if (_m.deviceId!=null) return false;
        if (userId!=null) {
            if (!userId.equals(_m.userId)) return false;
        } else if (_m.userId!=null) return false;

        if (msgContent==null&&_m.msgContent==null) return true;
        if (msgContent!=null&&_m.msgContent==null) return false;
        if (msgContent==null&&_m.msgContent!=null) return false;

        return msgContent.equals(_m.msgContent);
    }

    @Override
    public int hashCode() {
        return (msgType+"|"+affirm+"|"+fromType+"|"+toType+"|"+msgId+"|"+bizType+"|"+cmdType+"|"+command+"|"+returnType).hashCode();
    }
}