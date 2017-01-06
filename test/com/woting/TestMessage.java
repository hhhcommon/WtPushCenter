package com.woting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;

import com.spiritdata.framework.util.JsonUtils;
import com.woting.push.core.message.ByteConvert;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;

public class TestMessage {

    public static void main(String[] args) throws Exception {
//        System.out.println((char)Message.END_FIELD[0]+""+(char)Message.END_FIELD[1]);
//        System.out.println((char)Message.END_HEAD[0]+""+(char)Message.END_HEAD[1]);
//        byte[] test={'a','b','c','@', '|', '^'};
//        System.out.println(new String(test));
//        MsgMedia mm=new MsgMedia();
//        long l=-System.currentTimeMillis();
//        byte[] b=ByteConvert.long2bytes(l);
//        System.out.println(l);
////        for (int i=b.length-1; i>=0; i--) {
////            System.out.print(Integer.toBinaryString(b[i]));
////        }
//        System.out.println();
////        System.out.println(ByteConvert.bytes2long(b));
//        
//        int a=-1234;
//        b=ByteConvert.int2bytes(a);
//        System.out.println(a);
//        for (int i=b.length-1; i>=0; i--) {
//            System.out.print(Integer.toBinaryString(b[i]));
//        }
//        System.out.println();
//       // System.out.println(ByteConvert.bytes2int(b));
//
//        mm.setMsgType(0);
//        mm.setAffirm(1);
//        mm.setFromType(2);
//        mm.setToType(1);
//        mm.setMediaType(1);
//        mm.setSendTime(System.currentTimeMillis());
//        mm.setBizType(1);
//        mm.setTalkId("abcdef12");
//        mm.setSeqNo(1223);
//        mm.setMediaData(new byte[40]);
//
//        System.out.println(JsonUtils.objToJson(mm));
//        byte[] bbb=null;//mm.toBytes();
//        try {
//            File f=new File("d:\\bb.obj");
//            if (!f.exists()) f.createNewFile();
//            FileOutputStream fout=new FileOutputStream(f, false);
//            fout.write(bbb);
//            fout.flush();
//            fout.close();
//        } catch(Exception e) {
//            e.printStackTrace();
//        }
//        System.out.println("=============================");
//        String ssss="asdfwefasdfasdfasfdw||fasdaf";
//        /*
//     *            0000000000111111111122222222
//     *            0123456789012345678901234567
//     * byte[] ba="asdfwefasdfasdfasfdw||fasdaf";
//     * parse_String(ba, 3, 7, null)=10::fwefasd
//     * parse_String(ba, 15, 10, null)=22::asfdw
//     * parse_String(ba, 24, 10, null)=-1::sdaf
//         */
//        byte[] ba=ssss.getBytes();
////        System.out.println("==="+MessageUtils.parse_String(ba, 3, 7, null));
////        System.out.println("==="+mm.parse_String(ba, 15, 10, null));
////        System.out.println("==="+mm.parse_String(ba, 24, 10, null));
//        byte[] t=new byte[1];
//        t[0]=-128;
//        Byte __b=new Byte(t[0]);
//        int dddd=t[0];
//        System.out.println(dddd);
//        System.out.println(Integer.toBinaryString(__b));
//        MsgMedia mm2=null;//new MsgMedia(bbb);
//        System.out.println(JsonUtils.objToJson(mm2));
        System.out.println("==========================");
        byte[] msgBytes1={124, 94, 8, -94, -46, 88, 99, 89, 1, 0, 0, 66, 3, 53, 54, 52, 100, 97, 49, 100, 51, 51, 49, 53, 49, 124, 124, 16, 94, 94, -107, 0, 123, 34, 68, 101, 97, 108, 84, 121, 112, 101, 34, 58, 34, 49, 34, 44, 34, 71, 114, 111, 117, 112, 73, 110, 102, 111, 34, 58, 123, 34, 71, 114, 111, 117, 112, 78, 97, 109, 101, 34, 58, 34, -27, -123, -84, -27, -68, -128, -25, -66, -92, -27, -123, -91, -25, -69, -124, -26, -75, -117, -24, -81, -107, 34, 44, 34, 71, 114, 111, 117, 112, 68, 101, 115, 99, 110, 34, 58, 110, 117, 108, 108, 44, 34, 71, 114, 111, 117, 112, 73, 100, 34, 58, 34, 102, 54, 53, 49, 102, 55, 49, 101, 51, 52, 57, 55, 34, 125, 44, 34, 73, 110, 84, 121, 112, 101, 34, 58, 34, 49, 34, 44, 34, 68, 101, 97, 108, 84, 105, 109, 101, 34, 58, 34, 49, 52, 56, 51, 52, 51, 48, 52, 56, 50, 53, 53, 55, 34, 125};
        System.out.println(new String(msgBytes1));
        MsgNormal test12=new MsgNormal(msgBytes1);
        System.out.println(JsonUtils.objToJson(test12));
    }
}