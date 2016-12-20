package com.woting;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import com.woting.push.core.message.MessageUtils;

public class MessageDecode {
    public static void main(String[] args) throws IOException {
        byte[] ba=new byte[2048];
        byte[] endMsgFlag={0x00,0x00,0x00};
        short _dataLen=-3;
        int _headLen=36;

        //读取文件
        File f=new File("C:\\opt\\logs\\c_601673639_receive.保留.log");
        FileInputStream fis=new FileInputStream(f);

        boolean hasBeginMsg=false; //是否开始了一个消息
        boolean isOneMsg=false; //是否开始了一个消息
        int msgType=-1, r=-1, i=0, isAck=-1, isRegist=0;
        while ((r=fis.read())!=-1) {//读取一条消息
            ba[i++]=(byte)r;
            endMsgFlag[0]=endMsgFlag[1];
            endMsgFlag[1]=endMsgFlag[2];
            endMsgFlag[2]=(byte)r;
            if (!hasBeginMsg) {
                if (endMsgFlag[0]=='b'&&endMsgFlag[1]=='^'&&endMsgFlag[2]=='^') {
                    isOneMsg=true;//是心跳消息
                    continue;
                } else if ((endMsgFlag[0]=='|'&&endMsgFlag[1]=='^')||(endMsgFlag[0]=='^'&&endMsgFlag[1]=='|')) {
                    hasBeginMsg=true;
                    ba[0]=endMsgFlag[0];
                    ba[1]=endMsgFlag[1];
                    ba[2]=endMsgFlag[2];
                    i=3;
                    continue;
                } else if ((endMsgFlag[1]=='|'&&endMsgFlag[2]=='^')||(endMsgFlag[1]=='^'&&endMsgFlag[2]=='|')) {
                    hasBeginMsg=true;
                    ba[0]=endMsgFlag[1];
                    ba[1]=endMsgFlag[2];
                    i=2;
                    continue;
                }
                if (i>2) {
                    for (int n=1;n<=i;n++) ba[n-1]=ba[n];
                    --i;
                }
            } else {
                if (msgType==-1) msgType=MessageUtils.decideMsg(ba);
                if (msgType==0) {//0=控制消息(一般消息)
                    if (isAck==-1&&i==12) {
                        if (((ba[2]&0x80)==0x80)&&((ba[2]&0x00)==0x00)&&(((ba[i-1]&0xF0)==0x00)||((ba[i-1]&0xF0)==0xF0))) isAck=1; else isAck=0;
                        if ((ba[i-1]&0xF0)==0xF0) isRegist=1;
                    } else if (isAck==1) {//是回复消息
                        if (isRegist==1) { //是注册消息
                            if (i==48&&endMsgFlag[2]==0) _dataLen=80; else _dataLen=91;
                            if (_dataLen>=0&&i==_dataLen) {isOneMsg=true; continue;};
                        } else { //非注册消息
                            if (_dataLen<0) _dataLen=45;
                            if (_dataLen>=0&&i==_dataLen) break;
                        }
                    } else if (isAck==0) {//是一般消息
                        if (isRegist==1) {//是注册消息
                            if (((ba[2]&0x80)==0x80)&&((ba[2]&0x00)==0x00)) {
                                if (i==48&&endMsgFlag[2]==0) _dataLen=80; else _dataLen=91;
                            } else {
                                if (i==47&&endMsgFlag[2]==0) _dataLen=79; else _dataLen=90;
                            }
                            if (_dataLen>=0&&i==_dataLen) {isOneMsg=true; continue;};
                        } else {//非注册消息
                            if (_dataLen==-3&&endMsgFlag[1]=='^'&&endMsgFlag[2]=='^') _dataLen++;
                            else if (_dataLen>-3&&_dataLen<-1) _dataLen++;
                            else if (_dataLen==-1) {
                                _dataLen=(short)(((endMsgFlag[2]<<8)|endMsgFlag[1]&0xff));
                                if (_dataLen==0) {isOneMsg=true; continue;};
                            } else if (_dataLen>=0) {
                                if (--_dataLen==0) {isOneMsg=true; continue;};
                            }
                        }
                    }
                } else if (msgType==1) {//1=媒体消息
                    if (isAck==-1) {
                        if (((ba[2]&0x80)==0x80)&&((ba[2]&0x40)==0x00)) isAck=1; else isAck=0;
                    } else if (isAck==1) {//是回复消息
                        if (i==_headLen+1) {isOneMsg=true; continue;};
                    } else if (isAck==0) {//是一般媒体消息
                        if (i==_headLen+2) _dataLen=(short)(((ba[_headLen+1]<<8)|ba[_headLen]&0xff));
                        if (_dataLen>=0&&i==_dataLen+_headLen+2) {isOneMsg=true; continue;};
                    }
                }
            }
            if (isOneMsg) {
                byte[] mba=Arrays.copyOfRange(ba, 0, i);
                System.out.println(new String(mba));

                msgType=-1; r=-1; i=0; isAck=-1; isRegist=0;
                hasBeginMsg=false;
                _dataLen=-3;
                isOneMsg=false;
            }
        }
        fis.close();
    }
}