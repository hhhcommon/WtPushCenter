package com.woting.push.core.message;

import java.util.Arrays;

import com.spiritdata.framework.util.StringUtils;

/**
 * 媒体消息：媒体流数据
 * @author wanghui
 */
public class MsgMedia extends Message {
    private final static int COMPACT_LEN=24;

    private int mediaType; //流类型:1音频2视频
    private int bizType; //流业务类型:0对讲组；1电话
    private String talkId; //会话Id，或一次媒体传输的信道编号
    private int seqNo; //流中包的序列号
    private int returnType; //返回消息类型
    private byte[] mediaData; //包数据

    /**
     * 空构造函数
     */
    public MsgMedia() {
    }

    /**
     * 通过字节数组构造消息
     * @param binaryMsg 字节数组
     */
    public MsgMedia(byte[] binaryMsg) {
        this.fromBytes(binaryMsg);
    }

    public int getMediaType() {
        return mediaType;
    }
    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public int getBizType() {
        return bizType;
    }
    public void setBizType(int bizType) {
        this.bizType = bizType;
    }

    public String getTalkId() {
        return talkId;
    }
    public void setTalkId(String talkId) {
        this.talkId = talkId;
    }

    public int getSeqNo() {
        return seqNo;
    }
    public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
    }

    public int getReturnType() {
        return returnType;
    }
    public void setReturnType(int returnType) {
        this.returnType = returnType;
    }

    public byte[] getMediaData() {
        return mediaData;
    }
    public void setMediaData(byte[] mediaData) {
        this.mediaData = mediaData;
    }

    @Override
    public void fromBytes(byte[] binaryMsg) {
        if (binaryMsg.length<COMPACT_LEN) throw new RuntimeException("消息格式异常：长度不足！");
        if (!parse_EndFlagOk(binaryMsg)) throw new RuntimeException("消息未正确结束！");

        int _offset=0;
        byte f1=binaryMsg[_offset++];
        this.setMsgType(((f1&0x80)==0x80)?1:0);
        this.setAffirm(((f1&0x40)==0x40)?1:0);

        if (this.affirm==1&&this.msgType==1) throw new RuntimeException("消息格式异常：回复消息不需要确认！");
        if (this.msgType==1&&binaryMsg.length!=COMPACT_LEN+1) throw new RuntimeException("消息格式异常：回复消息长度错误！");

        if (((f1&0x10)==0x10)&&((f1&0x30)==0x10)) this.setFromType(1);//服务器
        else
        if (((f1&0x20)==0x20)&&((f1&0x30)==0x20)) this.setFromType(2);//设备
        else
        throw new RuntimeException("消息from位异常！");

        if (((f1&0x04)==0x04)&&((f1&0x0C)==0x04)) this.setToType(1);//服务器
        else
        if (((f1&0x08)==0x08)&&((f1&0x0C)==0x04)) this.setFromType(2);//设备
        else
        throw new RuntimeException("消息to位异常！");

        if (((f1&0x01)==0x01)&&((f1&0x03)==0x01)) this.setMediaType(1);//音频
        else
        if (((f1&0x02)==0x02)&&((f1&0x03)==0x02)) this.setFromType(2);//设备
        else
        throw new RuntimeException("消息媒体类型位异常！");

        this.setBizType(binaryMsg[_offset++]);

        byte[] _tempBytes=Arrays.copyOfRange(binaryMsg, _offset, _offset+8);//ByteBuffer.wrap(binaryMsg, _offset, 8).array();
        this.setSendTime(ByteConvert.bytes2long(_tempBytes));

        _offset+=8;
        String _tempStr;
        try {
            _tempStr=this.parse_String(binaryMsg, _offset, 8, null);            
        } catch(Exception e) {
            throw new RuntimeException("消息会话Id异常！", e);
        }
        String[] _sa=_tempStr.split("::");
        if (_sa.length!=2) throw new RuntimeException("消息会话Id异常！");
        if (Integer.parseInt(_sa[0])==-1) throw new RuntimeException("消息会话Id异常！");
        _offset=Integer.parseInt(_sa[0]);
        this.setTalkId(_sa[1]);

        _tempBytes=Arrays.copyOfRange(binaryMsg, _offset, _offset+4);
        this.setSeqNo(ByteConvert.bytes2int(_tempBytes));

        _offset+=4;
        if (this.msgType==1) this.setReturnType(binaryMsg[_offset]);
        else {
            this.mediaData=Arrays.copyOfRange(binaryMsg, _offset, binaryMsg.length-2);
        }
    }

    @Override
    public byte[] toBytes() {
        int _offset=0;
        int _bml=(mediaData==null?0:mediaData.length)+COMPACT_LEN+(this.msgType==1?1:0);
        byte[] ret=new byte[_bml];
        byte f1=0;
        if (this.msgType==1) f1|=0x80;
        if (this.affirm==1) f1|=0x40;
        f1|=(this.fromType==1?0x10:0x20);
        f1|=(this.toType==1?0x04:0x08);
        f1|=(this.mediaType==1?0x01:0x02);
        ret[0]=f1;

        ret[1]=(byte)this.bizType;

        _offset=2;
        byte[] _tempBytes=ByteConvert.long2bytes(this.sendTime);
        int i=0;
        for (; i<8; i++) ret[_offset+i]=_tempBytes[i];

        _offset=10;
        int talkIdLen=8;
        if (StringUtils.isNullOrEmptyOrSpace(this.talkId)) throw new RuntimeException("媒体消息异常：未设置有效会话id！");
        _tempBytes=this.talkId.getBytes();
        if (_tempBytes.length>=8) {
            for (i=0; i<8; i++) ret[_offset+i]=_tempBytes[i];
        } else if (_tempBytes.length==7) {
            for (i=0; i<7; i++) ret[_offset+i]=_tempBytes[i];
            ret[_offset+i]='E';
        } else {
            talkIdLen=_tempBytes.length+2;
            for (i=0; i<_tempBytes.length; i++) ret[_offset+i]=_tempBytes[i];
            ret[_offset+i]=END_FIELD[1];
            ret[_offset+i+1]=END_FIELD[0];
        }

        _offset+=talkIdLen;
        _tempBytes=ByteConvert.int2bytes(this.seqNo);
        for (i=0; i<4; i++) ret[_offset+i]=_tempBytes[i];

        _offset=22;
        i=0;
        if (this.msgType==1) {
            ret[_offset]=(byte)this.returnType;
            _offset++;
        } else {
            if (mediaData!=null) {
                for (; i<mediaData.length; i++) ret[_offset+i]=mediaData[i];
            }
        }

        ret[_offset+(i++)]=END_MSG[0];
        ret[_offset+i]=END_MSG[1];

        return ret;
    }
}