package com.woting.push.core.message;

import java.io.UnsupportedEncodingException;

/**
 * 消息处理中，对字节数组和消息内容的转换方法和公共判断方法的集合
 * @author wanghui
 */
public abstract class MessageUtils {
    /**
     * 判定消息的类型
     * @return 消息类型，目前只有0=控制消息(一般消息);1=媒体消息
     */
    public static int decideMsg(byte[] binaryMsg) {
        if (binaryMsg[0]=='|'&&binaryMsg[1]=='^') return 0;
        if (binaryMsg[0]=='^'&&binaryMsg[1]=='|') return 1;
        return 0;
    }
    /**
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
    protected static String parse_String(byte[] binaryMsg, int offset, int length, String encode) throws UnsupportedEncodingException {
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
            if (_endFieldFlag[1]==Message.END_FIELD[1]&&_endFieldFlag[0]==Message.END_FIELD[0]) {
                i-=1;
                break;
            }
        }
        String s=(encode==null?new String(binaryMsg, offset, i):new String(binaryMsg, offset, i, encode));
        return (nextOffset==-1?nextOffset:(nextOffset+offset))+"::"+s;
    }

    /**
     * 把一个字符串填入字节数组，填入的开始位置为偏移量offset，填入的字符串为ContStr，编码方式为encode。
     * 长度限制为length，注意，若字符串转码后的长于length，则被截取，若短于长度-2，会加入结束标志(结束标志为两个byte长)
     * 返回值为下一个可填入的偏移量。若长度大于或等于字节数组的长度返回-1，既是标识结束。
     * @param binaryMsg 字节数组
     * @param offset 填入偏移量
     * @param limitLen 长度限制
     * @param contStr 内容
     * @param encode 编码方式
     * @return 字节数组的新的填入偏移量
     * @throws UnsupportedEncodingException
     */
    protected static int set_String(byte[] binaryMsg, int offset, int limitLen, String contStr, String encode) throws UnsupportedEncodingException {
        int _bLen=binaryMsg.length;
        if (offset>=_bLen) return -1;
        byte[] _encodeByte=(encode==null?contStr.getBytes():contStr.getBytes(encode));
        int i=0, limit=Math.min(_encodeByte.length, Math.min(limitLen, _bLen-offset));
        for (;i<limit; i++) {
            binaryMsg[i+offset]=_encodeByte[i];
        }
        if (limit==_bLen-offset) return -1;
        if (i==limitLen) {
            return offset+limitLen;
        } else if (i==limitLen-1) {
            binaryMsg[i+offset-1]=Message.END_FIELD[1];
            binaryMsg[i+offset]=Message.END_FIELD[0];
            return offset+limitLen;
        } else {
            binaryMsg[i+offset]=Message.END_FIELD[1];
            binaryMsg[i+offset+1]=Message.END_FIELD[0];
            return offset+_encodeByte.length+2;
        }
    }

    /**
     * 根据原始一般消息(非媒体消息)生成应答消息
     * @param orgMsg 一般消息
     * @return 应答消息
     */
    public static MsgNormal buildAckMsg(MsgNormal orgMsg) {
        MsgNormal ret=new MsgNormal();

        ret.setAffirm(0);
        ret.setMsgType(1);
        ret.setSendTime(System.currentTimeMillis());
        ret.setFromType(orgMsg.getToType());
        ret.setToType(orgMsg.getFromType());

        ret.setBizType(0);
        ret.setCmdType(0);
        ret.setReMsgId(orgMsg.getMsgId());

        return ret;
    }

    /**
     * 根据原始媒体消息(非媒体消息)生成应答消息
     * @param orgMsg 一般消息
     * @param returnType 返回类型
     * @return 应答消息
     */
    public static MsgMedia buildAckMsg(MsgMedia orgMsg, int returnType) {
        MsgMedia ret=new MsgMedia();

        ret.setAffirm(0);
        ret.setMsgType(1);
        ret.setSendTime(System.currentTimeMillis());
        ret.setFromType(orgMsg.getToType());
        ret.setToType(orgMsg.getFromType());

        ret.setBizType(orgMsg.getBizType());
        ret.setMediaType(orgMsg.getMediaType());
        ret.setTalkId(orgMsg.getTalkId());
        ret.setSeqNo(orgMsg.getSeqNo());
        ret.setReturnType(returnType);

        ret.setObjId(orgMsg.getObjId()); //准备删除

        return ret;
    }

    /**
     * 根据字符串数组创建消息对象
     * @param binaryMsg
     * @return
     * @throws Exception 
     */
    public static Message buildMsgByBytes(byte[] binaryMsg) throws Exception {
        int msgType=decideMsg(binaryMsg);
        if (msgType==0) return new MsgNormal(binaryMsg);
        else
        if (msgType==1) return new MsgMedia(binaryMsg);
        else
        return null;
    }
}