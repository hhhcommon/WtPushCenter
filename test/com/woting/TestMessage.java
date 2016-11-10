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
        System.out.println((char)Message.END_FIELD[0]+""+(char)Message.END_FIELD[1]);
        System.out.println((char)Message.END_HEAD[0]+""+(char)Message.END_HEAD[1]);
        byte[] test={'a','b','c','@', '|', '^'};
        System.out.println(new String(test));
        MsgMedia mm=new MsgMedia();
        long l=-System.currentTimeMillis();
        byte[] b=ByteConvert.long2bytes(l);
        System.out.println(l);
//        for (int i=b.length-1; i>=0; i--) {
//            System.out.print(Integer.toBinaryString(b[i]));
//        }
        System.out.println();
//        System.out.println(ByteConvert.bytes2long(b));
        
        int a=-1234;
        b=ByteConvert.int2bytes(a);
        System.out.println(a);
        for (int i=b.length-1; i>=0; i--) {
            System.out.print(Integer.toBinaryString(b[i]));
        }
        System.out.println();
       // System.out.println(ByteConvert.bytes2int(b));

        mm.setMsgType(0);
        mm.setAffirm(1);
        mm.setFromType(2);
        mm.setToType(1);
        mm.setMediaType(1);
        mm.setSendTime(System.currentTimeMillis());
        mm.setBizType(1);
        mm.setTalkId("abcdef12");
        mm.setSeqNo(1223);
        mm.setMediaData(new byte[40]);

        System.out.println(JsonUtils.objToJson(mm));
        byte[] bbb=null;//mm.toBytes();
        try {
            File f=new File("d:\\bb.obj");
            if (!f.exists()) f.createNewFile();
            FileOutputStream fout=new FileOutputStream(f, false);
            fout.write(bbb);
            fout.flush();
            fout.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println("=============================");
        String ssss="asdfwefasdfasdfasfdw||fasdaf";
        /*
     *            0000000000111111111122222222
     *            0123456789012345678901234567
     * byte[] ba="asdfwefasdfasdfasfdw||fasdaf";
     * parse_String(ba, 3, 7, null)=10::fwefasd
     * parse_String(ba, 15, 10, null)=22::asfdw
     * parse_String(ba, 24, 10, null)=-1::sdaf
         */
        byte[] ba=ssss.getBytes();
//        System.out.println("==="+MessageUtils.parse_String(ba, 3, 7, null));
//        System.out.println("==="+mm.parse_String(ba, 15, 10, null));
//        System.out.println("==="+mm.parse_String(ba, 24, 10, null));
        byte[] t=new byte[1];
        t[0]=-128;
        Byte __b=new Byte(t[0]);
        int dddd=t[0];
        System.out.println(dddd);
        System.out.println(Integer.toBinaryString(__b));
        MsgMedia mm2=null;//new MsgMedia(bbb);
        System.out.println(JsonUtils.objToJson(mm2));
        System.out.println("==========================");
        byte[] msgBytes1={124, 94, -128, -78, -75, -95, 71, 88, 1, 0, 0, 33, 9, 3, 54, 49, 100, 54, 52, 49, 100, 57, 54, 101, 50, 49, 124, 124, 57, 54, 50, 49, 101, 97, 53, 51, 54, 53, 48, 98, 52, 50, 101, 99, 98, 55, 50, 52, 56, 56, 55, 50, 56, 54, 97, 99, 54, 51, 57, 50, 16, 94, 94, 87, 0, 123, 34, 67, 97, 108, 108, 101, 100, 101, 114, 73, 100, 34, 58, 34, 101, 57, 97, 56, 102, 99, 49, 101, 97, 55, 52, 53, 34, 44, 34, 67, 97, 108, 108, 73, 100, 34, 58, 34, 49, 50, 51, 52, 53, 54, 55, 56, 49, 50, 51, 52, 53, 54, 55, 56, 57, 97, 98, 99, 34, 44, 34, 67, 97, 108, 108, 101, 114, 73, 100, 34, 58, 34, 52, 50, 51, 48, 48, 55, 99, 102, 49, 102, 98, 49, 34, 125};
        MsgNormal test12=new MsgNormal(msgBytes1);
        System.out.println(JsonUtils.objToJson(test12));
    }
}