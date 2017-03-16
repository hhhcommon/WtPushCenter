package com.woting.push.core.message;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.spiritdata.framework.util.StringUtils;

/**
 * 媒体消息：媒体流数据
 * @author wanghui
 */
public class MsgMedia extends Message {
    private static final long serialVersionUID=-3827446721333425724L;

    private final static int COMPACT_LEN=38;//若删除ObjId，则这个值为24

    private int mediaType; //流类型:1音频2视频
    private int bizType; //流业务类型:1对讲组；2电话
    private String channelId; //频道Id:在组对讲中是组Id,在电话对讲中是电话通话Id
    private String talkId; //会话Id，或一次媒体传输的信道编号
    private int seqNo; //流中包的序列号
    private int returnType; //返回消息类型
    private byte[] mediaData=null; //包数据

    private Object extInfo=null;//扩展信息，这个信息是不参与传输的
    public Object getExtInfo() {
        return extInfo;
    }
    public void setExtInfo(Object extInfo) {
        this.extInfo=extInfo;
    }

    /**
     * 空构造函数
     */
    public MsgMedia() {
    }

    /**
     * 通过字节数组构造消息
     * @param binaryMsg 字节数组
     * @throws Exception 
     */
    public MsgMedia(byte[] binaryMsg) throws Exception {
        fromBytes(binaryMsg);
    }

    public int getMediaType() {
        return mediaType;
    }
    public void setMediaType(int mediaType) {
        this.mediaType=mediaType;
    }

    public int getBizType() {
        return bizType;
    }
    public void setBizType(int bizType) {
        this.bizType=bizType;
    }

    public String getChannelId() {
        return channelId;
    }
    public void setChannelId(String channelId) {
        this.channelId=channelId;
    }

    public String getTalkId() {
        return talkId;
    }
    public void setTalkId(String talkId) {
        this.talkId=talkId;
    }

    public int getSeqNo() {
        return seqNo;
    }
    public void setSeqNo(int seqNo) {
        this.seqNo=seqNo;
    }

    public int getReturnType() {
        return returnType;
    }
    public void setReturnType(int returnType) {
        this.returnType=returnType;
    }

    public byte[] getMediaData() {
        return mediaData;
    }
    public void setMediaData(byte[] mediaData) {
        this.mediaData=mediaData;
    }

    @Override
    public void fromBytes(byte[] binaryMsg) throws Exception {
        if (MessageUtils.decideMsg(binaryMsg)!=1) throw new Exception("非数据包格式错误！");
        if (MessageUtils.endOK(binaryMsg)!=1) throw new Exception("消息未正常结束！");

        int _offset=2;
        byte f1=binaryMsg[_offset++];
        setMsgType(((f1&0x80)==0x80)?1:0);
        setAffirm(((f1&0x40)==0x40)?1:0);

        if (affirm==1&&msgType==1) throw new Exception("消息格式异常：回复消息不需要确认！");
        if (msgType==1&&binaryMsg.length!=COMPACT_LEN+1) throw new Exception("消息格式异常：回复消息长度错误！");

        if ((f1&0x30)==0x10) setFromType(1);//服务器
        else
        if ((f1&0x30)==0x20) setFromType(0);//设备
        else
        throw new Exception("消息from位异常！");

        if ((f1&0x0C)==0x04) setToType(1);//服务器
        else
        if ((f1&0x0C)==0x08) setToType(0);//设备
        else
        throw new Exception("消息to位异常！");

        if ((f1&0x03)==0x01) setMediaType(1);//音频
        else
        if ((f1&0x02)==0x02) setMediaType(2);//视频
        else
        throw new Exception("消息媒体类型位异常！");

        setBizType(binaryMsg[_offset++]);

        byte[] _tempBytes=Arrays.copyOfRange(binaryMsg, _offset, _offset+8);//ByteBuffer.wrap(binaryMsg, _offset, 8).array();
        setSendTime(ByteConvert.bytes2long(_tempBytes));

        _offset+=8;
        String _tempStr;
        try {
            _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 8, null);
        } catch(Exception e) {
            throw new Exception("消息会话Id异常！", e);
        }
        String[] _sa=_tempStr.split("::");
        if (_sa.length!=2) throw new Exception("消息会话Id异常！");
        if (Integer.parseInt(_sa[0])==-1) throw new Exception("消息会话Id异常！");
        _offset=Integer.parseInt(_sa[0]);
        setTalkId(_sa[1]);

        _tempBytes=Arrays.copyOfRange(binaryMsg, _offset, _offset+4);
        setSeqNo(ByteConvert.bytes2int(_tempBytes));

        _offset+=4;

        try {
            _tempStr=MessageUtils.parse_String(binaryMsg, _offset, 12, null);
        } catch(Exception e) {
            throw new Exception("对象Id异常！", e);
        }
        _sa=_tempStr.split("::");
        if (_sa.length!=2) throw new Exception("对象Id异常！");
        if (Integer.parseInt(_sa[0])==-1) throw new Exception("对象Id异常！");
        _offset=Integer.parseInt(_sa[0]);
        setChannelId(_sa[1]);

        if (isAck()) setReturnType(binaryMsg[_offset]);
        else {
            short len=(short)(((binaryMsg[_offset+1]<<8)|binaryMsg[_offset]&0xff));
            if (len>0) mediaData=Arrays.copyOfRange(binaryMsg, _offset+2, _offset+2+len);
        }
    }

    @Override
    public byte[] toBytes() throws Exception {
        int _offset=0;
        byte[] ret=new byte[_MAXLENGTH];
        byte zeroByte=0;

        ret[_offset++]=BEGIN_MDA[0];
        ret[_offset++]=BEGIN_MDA[1];

        if (msgType==1) zeroByte|=0x80;
        if (affirm==1) zeroByte|=0x40;
        zeroByte|=(fromType==1?0x10:0x20);
        zeroByte|=(toType==1?0x04:0x08);
        zeroByte|=(mediaType==1?0x01:0x02);
        ret[_offset++]=zeroByte;

        ret[_offset++]=(byte)bizType;

        byte[] _tempBytes=ByteConvert.long2bytes(sendTime);
        int i=0;
        for (; i<8; i++) ret[_offset++]=_tempBytes[i];

        if (StringUtils.isNullOrEmptyOrSpace(talkId)) throw new Exception("媒体消息异常：未设置有效会话id！");
        try {
            _offset=MessageUtils.set_String(ret, _offset, 8, talkId, null);
        } catch (UnsupportedEncodingException e) {
        }

        _tempBytes=ByteConvert.int2bytes(seqNo);
        for (i=0; i<4; i++) ret[_offset++]=_tempBytes[i];

        //为objId，要删除掉
        _tempBytes=channelId.getBytes();
        for (i=0; i<12; i++) ret[_offset++]=_tempBytes[i];
        //为objId，要删除掉

        if (msgType==1) ret[_offset++]=(byte)returnType;

        if (!isAck()) {
            short len=(short)(mediaData==null?0:mediaData.length);
            ret[_offset++]=(byte)(len>>0);
            ret[_offset++]=(byte)(len>>8);
            if (mediaData!=null) {
                for (i=0; i<mediaData.length; i++) ret[_offset++]=mediaData[i];
            }
        }

        ret[_offset++]=Message.END_MSG[0];
        ret[_offset++]=Message.END_MSG[1];
        byte[] _ret=Arrays.copyOfRange(ret, 0, _offset);
        return _ret;
    }

    /**
     * 判断是否是应答消息
     */
    public boolean isAck() {
        return affirm==0&&msgType==1;
    }

    /**
     * 比较两个媒体包是否相同
     * @param msg 另一个参与比较的类
     * @return 相同返回true，否则返回false
     */
    public boolean equals(Message msg) {
        if (!(msg instanceof MsgMedia)) return false;

        if (!equalsMsg(msg)) return false;

        MsgMedia _m=(MsgMedia)msg;
        if (bizType!=_m.bizType) return false;
        if (channelId!=null) if (!channelId.equals(_m.channelId)) return false;
        else if (_m.channelId!=null) return false;
        if (mediaType!=_m.mediaType) return false;
        if (talkId!=null) if (!talkId.equals(_m.talkId)) return false;
        else if (_m.talkId!=null) return false;
        if (returnType!=_m.returnType) return false;
        if (seqNo!=_m.seqNo) return false;

        if (mediaData!=null&&_m.mediaData!=null) {
            if (mediaData.length!=_m.mediaData.length) return false;
            for (int i=0; i<mediaData.length; i++) {
                if (mediaData[i]!=_m.mediaData[i]) return false;
            }
        }
        else if (mediaData==null&&_m.mediaData!=null) return false;
        else if (mediaData!=null&&_m.mediaData==null) return false;
        return true;
    }

    /**
     * 是否是音频包
     * @return
     */
    public boolean isAudio() {
        return mediaType==1;
    }

    /**
     * 是否是视频包
     * @return
     */
    public boolean isVedio() {
        return mediaType==2;
    }

    protected boolean equalsMsg(Message msg) {
        if (msgType!=msg.msgType) return false;
        if (affirm!=msg.affirm) return false;
        if (fromType!=msg.fromType) return false;
        if (toType!=msg.toType) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return (msgType+"|"+affirm+"|"+fromType+"|"+toType+"|"+mediaType+"|"+bizType+"|"+channelId+"|"+talkId+"|"+seqNo).hashCode();
    }
}