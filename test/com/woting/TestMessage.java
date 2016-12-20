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
        byte[] msgBytes1={124, 94, 8, 122, 44, -98, 27, 89, 1, 0, 0, -127, 4, 97, 98, 99, 50, 101, 98, 56, 51, 97, 51, 101, 51, 124, 124, 17, 3, 65, 112, 112, 69, 110, 103, 105, 110, 48, 48, 48, 49, 65, 112, 112, 69, 110, 103, 105, 110, 83, 101, 114, 118, 101, 114, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 49, 94, 94, -16, 1, 123, 34, 85, 115, 101, 114, 73, 110, 102, 111, 34, 58, 123, 34, 98, 105, 114, 116, 104, 100, 97, 121, 34, 58, 34, 50, 48, 49, 54, 45, 49, 49, 45, 49, 52, 32, 49, 52, 58, 49, 55, 58, 52, 56, 34, 44, 34, 117, 115, 101, 114, 78, 117, 109, 34, 58, 34, 73, 68, 87, 69, 73, 89, 73, 49, 52, 48, 52, 34, 44, 34, 115, 116, 97, 114, 83, 105, 103, 110, 34, 58, 34, -27, -113, -116, -27, -83, -112, -27, -70, -89, 34, 44, 34, 117, 115, 101, 114, 67, 108, 97, 115, 115, 34, 58, 34, 48, 34, 44, 34, 108, 111, 103, 34, 58, 34, 76, 111, 103, 103, 101, 114, 91, 99, 111, 109, 46, 119, 111, 116, 105, 110, 103, 46, 112, 97, 115, 115, 112, 111, 114, 116, 46, 85, 71, 65, 46, 112, 101, 114, 115, 105, 115, 46, 112, 111, 106, 111, 46, 85, 115, 101, 114, 80, 111, 93, 34, 44, 34, 110, 105, 99, 107, 78, 97, 109, 101, 34, 58, 34, 77, 121, 78, 105, 99, 107, 78, 97, 109, 101, 34, 44, 34, 67, 84, 105, 109, 101, 34, 58, 34, 50, 48, 49, 54, 45, 49, 48, 45, 49, 52, 32, 49, 51, 58, 53, 56, 58, 50, 51, 34, 44, 34, 109, 97, 105, 108, 65, 100, 100, 114, 101, 115, 115, 34, 58, 34, 122, 64, 115, 46, 99, 111, 34, 44, 34, 117, 115, 101, 114, 73, 100, 34, 58, 34, 56, 50, 51, 97, 54, 51, 102, 51, 52, 56, 98, 55, 34, 44, 34, 109, 97, 105, 110, 80, 104, 111, 110, 101, 78, 117, 109, 34, 58, 34, 110, 117, 108, 108, 34, 44, 34, 115, 101, 114, 105, 97, 108, 86, 101, 114, 115, 105, 111, 110, 85, 73, 68, 34, 58, 34, 45, 50, 52, 54, 55, 48, 53, 48, 49, 57, 53, 51, 50, 49, 49, 52, 51, 53, 56, 57, 34, 44, 34, 112, 97, 115, 115, 119, 111, 114, 100, 34, 58, 34, 49, 50, 51, 52, 53, 54, 34, 44, 34, 117, 115, 101, 114, 83, 105, 103, 110, 34, 58, 34, 83, 97, 116, 117, 114, 100, 97, 121, 34, 44, 34, 117, 115, 101, 114, 83, 116, 97, 116, 101, 34, 58, 34, 48, 34, 44, 34, 108, 111, 103, 105, 110, 78, 97, 109, 101, 34, 58, 34, -26, -105, -96, -26, -119, -117, -26, -100, -70, -27, -113, -73, -26, -75, -117, -24, -81, -107, 34, 44, 34, 117, 115, 101, 114, 84, 121, 112, 101, 34, 58, 34, 49, 34, 44, 34, 108, 109, 84, 105, 109, 101, 34, 58, 34, 50, 48, 49, 54, 45, 49, 49, 45, 49, 52, 32, 49, 52, 58, 49, 55, 58, 52, 56, 34, 125, 44, 34, 71, 114, 111, 117, 112, 73, 100, 34, 58, 34, 99, 50, 53, 49, 55, 56, 54, 48, 56, 53, 97, 101, 34, 125};
        System.out.println(new String(msgBytes1));
        MsgNormal test12=new MsgNormal(msgBytes1);
        System.out.println(JsonUtils.objToJson(test12));
    }
}