package com.woting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;

import com.spiritdata.framework.util.JsonUtils;
import com.woting.push.core.message.ByteConvert;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;

public class TestMessage {

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.out.println((char)Message.END_FIELD[0]+""+(char)Message.END_FIELD[1]);
        System.out.println((char)Message.END_HEAD[0]+""+(char)Message.END_HEAD[1]);
        System.out.println((char)Message.END_MSG[0]+""+(char)Message.END_MSG[1]);
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
        System.out.println(ByteConvert.bytes2long(b));
        
        int a=-1234;
        b=ByteConvert.int2bytes(a);
        System.out.println(a);
        for (int i=b.length-1; i>=0; i--) {
            System.out.print(Integer.toBinaryString(b[i]));
        }
        System.out.println();
        System.out.println(ByteConvert.bytes2int(b));

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
        byte[] bbb=mm.toBytes();
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
        System.out.println("==="+mm.parse_String(ba, 3, 7, null));
        System.out.println("==="+mm.parse_String(ba, 15, 10, null));
        System.out.println("==="+mm.parse_String(ba, 24, 10, null));
        byte[] t=new byte[1];
        t[0]=-128;
        Byte __b=new Byte(t[0]);
        int dddd=t[0];
        System.out.println(dddd);
        System.out.println(Integer.toBinaryString(__b));
        MsgMedia mm2=new MsgMedia(bbb);
        System.out.println(JsonUtils.objToJson(mm2));
    }
}