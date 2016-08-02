package com.wotingfm.activity.interphone.commom.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class udp  {
    private byte[] buf; 
    private DatagramSocket socket;
    private String host="182.92.175.134";
    private int sendPort=9999;
    private int recPort=6666;

    public static void main(String[] args) {
        new udp().startServer();
    }

    public void startServer() {
        new udpSendThread().start();  //开启udp发送线程
        new udpReceiveThread().start();//开启udp接收线程
    }

    //UDP数据发送线程
    public class udpSendThread extends Thread{
        @Override
        public void run()   {
            try {
                String msg="小辛测试";
                buf=msg.getBytes();

                socket = new DatagramSocket(sendPort);
                InetAddress serverAddr = InetAddress.getByName(host);
                int num=0;
                while(true){
                    if(num<1000){
                        DatagramPacket outPacket = new DatagramPacket(buf, buf.length,serverAddr,sendPort);  
                        socket.send(outPacket);
                        num++;
                        System.out.println("udp发送的数据");
                    }else{
                        break;
                    }
                }
                socket.close();
            } catch (Exception e) {
            }  
        }
    }

    //UDP数据接收线程
    public class udpReceiveThread extends Thread{   
        @Override  
        public void run() {  
            try {  
                socket = new DatagramSocket(recPort);
                while(true){
                    byte[] inBuf= new byte[1024];
                    DatagramPacket inPacket=new DatagramPacket(inBuf,inBuf.length);
                    socket.receive(inPacket);
                    String receiveInfo = new String (inPacket.getData());       
                    System.out.println("接收到的udp数据"+receiveInfo+"");
                }               
            } catch (Exception e) {  
                socket.close();
                new udpReceiveThread().interrupt();
            }  
        }  
    }
}