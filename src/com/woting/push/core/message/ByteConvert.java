package com.woting.push.core.message;

/**
 * 字节与各种数据类型之间的转换
 * @author wanghui
 */
public abstract class ByteConvert {
    /**
     * 长整型转换为字节数组
     * @param x 长整型整数
     * @return 字节数组
     */
    public static byte[] long2bytes(long x) {
        byte[] ret=new byte[8];
        ret[0]=(byte)(x    );
        ret[1]=(byte)(x>> 8);
        ret[2]=(byte)(x>>16);
        ret[3]=(byte)(x>>24);
        ret[4]=(byte)(x>>32);
        ret[5]=(byte)(x>>40);
        ret[6]=(byte)(x>>48);
        ret[7]=(byte)(x>>56);
        return ret;
    }

    /**
     * 字节数组转换为长整型
     * @param bytes 字节数组
     * @return 长整型整数
     * @throws Exception 
     */
    public static long bytes2long(byte[] bytes) throws Exception {
        if (bytes.length!=8) throw new Exception("字节数组转换为长整型：字节数组长度必须是8!");
        return
          ((((long)bytes[7])     <<56) |
           (((long)bytes[6]&0xff)<<48) |
           (((long)bytes[5]&0xff)<<40) |
           (((long)bytes[4]&0xff)<<32) |
           (((long)bytes[3]&0xff)<<24) |
           (((long)bytes[2]&0xff)<<16) |
           (((long)bytes[1]&0xff)<< 8) |
           (((long)bytes[0]&0xff)));
    }

    /**
     * 整型转换为字节数组
     * @param x 整型整数
     * @return 字节数组
     */
    public static byte[] int2bytes(int x) {
        byte[] ret=new byte[4];
        ret[0]=(byte)(x    );
        ret[1]=(byte)(x>> 8);
        ret[2]=(byte)(x>>16);
        ret[3]=(byte)(x>>24);
        return ret;
    }

    /**
     * 字节数组转换为整型
     * @param bytes 字节数组
     * @return 整型整数
     * @throws Exception 
     */
    public static int bytes2int(byte[] bytes) throws Exception {
        if (bytes.length!=4) throw new Exception("字节数组转换为整型：字节数组长度必须是4!");
        return
          ((((int)bytes[3])     <<24) |
           (((int)bytes[2]&0xff)<<16) |
           (((int)bytes[1]&0xff)<< 8) |
           (((int)bytes[0]&0xff)));
    }
}