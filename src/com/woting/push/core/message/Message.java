package com.woting.push.core.message;

import java.io.UnsupportedEncodingException;

/**
 * 消息接口，主要是消息序列化和反序列化。
 * @author wanghui
 */
public abstract class Message {
    public final static byte[] END_FIELD={124, 124}; //字段结束标识||
    public final static byte[] END_HEAD={94, 94}; //消息头结束标识
    public final static byte[] END_MSG={124, 94}; //消息结束标识
    
    protected int msgType; //消息类型:0主动发出；1回复类型
    protected int affirm; //是否需要确认;0不需要1需要，默认值=0不需要确认
    protected long sendTime; //发送时间

    //1服务器；2设备
    protected int fromType; //从那类设备来
    protected int toType; //到那类设备去

    public int getMsgType() {
        return msgType;
    }
    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }

    public int getAffirm() {
        return affirm;
    }
    public void setAffirm(int affirm) {
        this.affirm = affirm;
    }

    public long getSendTime() {
        return sendTime;
    }
    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public int getFromType() {
        return fromType;
    }
    public void setFromType(int fromType) {
        this.fromType = fromType;
    }

    public int getToType() {
        return toType;
    }
    public void setToType(int toType) {
        this.toType = toType;
    }

    /**
     * 从字节数组中获得消息
     */
    public abstract void fromBytes(byte[] binaryMsg);

    /**
     * 把消息序列化为字节数组
     * @return 消息对应的字节数组
     */
    public abstract byte[] toBytes();

    protected boolean parse_EndFlagOk(byte[] binaryMsg) {
        if (binaryMsg.length<=2) return false;
        int _bl=binaryMsg.length;
        return binaryMsg[_bl-1]==END_MSG[1]&&binaryMsg[_bl-2]==END_MSG[0];
    }

    /*
     * 从binaryMsg字节数组的offset开始，取length长度的字符串，并按照encode所指定的编码方式进行编码。
     * <br/>注意:
     * <pre>
     * 1-若从offset到length间有结束标识END_FIELD，则所取字符串到此为止。
     * 2-返回值包括下一个位置的偏移量和所取得的字符串。
     * </pre>
     * 例如:
     * <blockquote><pre>
     *            0000000000111111111122222222
     *            0123456789012345678901234567
     * byte[] ba="asdfwefasdfasdfasfdw||fasdaf";
     * parse_String(ba, 3, 7, null)=10::fwefasd
     * parse_String(ba, 15, 10, null)=22::asfdw
     * parse_String(ba, 24, 10, null)=-1::sdaf
     * </pre></blockquote>
     * @param binaryMsg 字节数据
     * @param offset 开始偏移量
     * @param length 获取长度
     * @param encode 编码方式
     * @return 下一个开始的位置::字符串，若下一个位置到数组末尾，则为-1
     * @throws UnsupportedEncodingException 
     */
    public String parse_String(byte[] binaryMsg, int offset, int length, String encode) throws UnsupportedEncodingException {
        byte[] _subByte=new byte[length];
        int nextOffset=0;
        byte[] _endFieldFlag=new byte[2];
        int i=0;
        for (; i<length; i++) {
            if (offset+i>=binaryMsg.length) {
                nextOffset=-1;
                break;
            }
            _endFieldFlag[1]=_endFieldFlag[0];
            _endFieldFlag[0]=binaryMsg[offset+i];
            _subByte[nextOffset++]=binaryMsg[offset+i];
            if (_endFieldFlag[1]==END_FIELD[1]&&_endFieldFlag[0]==END_FIELD[0]) {
                i-=1;
                break;
            }
        }
        String s=(encode==null?new String(binaryMsg, offset, i):new String(binaryMsg, offset, i, encode));
        return (nextOffset==-1?nextOffset:(nextOffset+offset))+"::"+s;
    }
}