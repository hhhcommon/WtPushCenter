//package Test;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.concurrent.ConcurrentLinkedQueue;
//import java.util.concurrent.LinkedBlockingQueue;
//
//import android.annotation.SuppressLint;
//import android.media.AudioFormat;
//import android.media.AudioManager;
//import android.media.AudioTrack;
//import android.os.Environment;
//import android.util.Log;
//
//import com.rectsoft.ppsip.G729ACodec;
//import com.wotingfm.activity.interphone.commom.base64.Base64;
//import com.wotingfm.utils.BytesTransUtils;
//
///**
// * 播放某一个talkId下的内容，这是一个线程类
// * WH
// */
////这个类：一创建就开始播放过程了
//public class TalkPlayer extends Thread {
//    //以下这些数据是参数化数据，放在一个config类中更好
//    private long packMILLISECONDS=40;       //每个包的播放时间：毫秒
//    //播放模型参数：第一次延时控制——开始播放要达到的条件，延时多长时间或凑足多少个包
//    private int firstDelayP=20;             //首次延时：包时长，多少个包时长
//    private long firstDelayT=-1;            //首次延时：绝对时间，若>=0，则包时长失效
//    private int firstContinuePack=20;       //首次延时：连续包长度，连续多少个包就可以播放了
//    //播放模型参数：过期判断——多长时间未收到包，就不播放了
//    private int expiredP=20;               //过期判断：包时长，多少个包的时长
//    private long expiredT=3000;            //过期判断：绝对时间，若>=0，则包时长失效
//    //播放模型参数：高级-缓冲
//    private int cacheSize=10;             //缓冲区大小，毫秒
//    private float playCacheRate=0.8f;       //缓冲填满率为多少后就可以播放了，毫秒
//    private long interTolerateT=40;        //间隔容忍时长，两个包间隔多长时间播放是可以容忍的，毫秒
//
//    private LinkedBlockingQueue<String> logQueue=new LinkedBlockingQueue<String>();//写日志
//    private WriteLog wl;
//
//    //对象
//    private String talkId;                            //说话Id
//    private AudioTrack audioTrack;                    //android播放类
//    //private G729ACodec codec;                       //音频数据解码类
//    private HashMap<Integer, byte[]> receiveVedioMap; //接收音频的数组Map
//
//    //控制
//    private boolean stopFlag=false; //是否结束，若stopFlag=false，则标识正在运行
//    private boolean tobeStop=false; //是否将要停止播放
//    private int initFlag=0;         //是否初始化完成
//    private int endNum=0;           //结束包的序号
//    private int maxNum=0;           //当前收到包的最大序号
//    private G729ACodec codec;
//    private static ConcurrentLinkedQueue<String> timeMsgQueue; //打印日志的数据消息队列
//    /**
//     * 构造函数，必须用一个talkId来构造这个类
//     */
//    @SuppressLint("UseSparseArrays")
//    public TalkPlayer(String talkId) throws Exception {
//        super();
//        //0、全局控制参数
//        initFlag=0;
//        tobeStop=false;
//        stopFlag=false;
//        //1、设置说话Id
//        if (talkId==null||talkId.trim().length()==0) throw new IllegalArgumentException("必须设置talkId");
//        this.talkId=talkId;
//        //2、初始化接收音频的数组Map
//        receiveVedioMap=new HashMap<Integer, byte[]>();
//        //3、初始化音频数据解码类
//        codec=G729ACodec.getInstance();
//        codec.initDecoder();
//        timeMsgQueue = new ConcurrentLinkedQueue<String>();//初始化打印日志的数据消息队列
//        //写日志的线程
//        WriteReceive wr=new WriteReceive();
//        wr.start();
//        //4、启动线程
//        this.start();
//        wl=new WriteLog();
//        wl.start();
//    }
//
//    //是否初始化完成
//    public boolean isInitOk() {
//        return initFlag==1;
//    }
//
//    /**
//     * 获得本播放的TalkId
//     */
//    public String getTalkId() {
//        return talkId;
//    }
//
//    /**
//     * 看播放是否已经结束了
//     */
//    public boolean isStop() {
//        return stopFlag&&initFlag==1;
//    }
//
//    /**
//     * 停止播放：调用者可以通过这个方法停止播放
//     */
//    public void stopPlayer() {
//        tobeStop=true;
//    }
//
//    /**
//     * 把收到的语音包数据放入“音频的数组Map”，这个方法只做这一件事情
//     * @param mResults 音频数据，是base64后的字符串
//     * @param seqNum 音频包序号
//     * @param talkId 说话Id
//     */
//    public void receiveVedioPack(String mResults ,int seqNum, String talkId) {
//        if (receiveVedioMap==null){
//            Log.i("判断数据包A","receiveVedioMap==null");
//            return; //若接收因频数据的Map为空，则什么也不做
//        }
//        if (!talkId.equals(this.talkId)) {
//            Log.i("判断数据包B","说话Id不匹配");
//            return; //若说话Id不匹配，不做处理
//        }
//        if ((seqNum<0||seqNum>endNum)&&endNum>0){
//            Log.i("判断数据包C","负数包或大于结束包");
//            return; //若在已知结束包号的情况下，收到了负数包或大于结束包号的包，则不做处理
//        }
//        if (seqNum<0&&endNum==0) {
//            Log.i("判断数据包D","收到结束包");
//            endNum=-seqNum;
//            maxNum=-seqNum;
//        } else {
//            maxNum=seqNum>maxNum?seqNum:maxNum;
//        }
//
//        Log.e("解码前时间","TalkId=="+talkId+"::Rtime=="+System.currentTimeMillis()+"::SeqNum=="+seqNum);
//        byte[] vedioData=uncompress(Base64.decode(mResults)); //在这里解码，**重要
//        Log.e("解码后时间","TalkId=="+talkId+"::Rtime=="+System.currentTimeMillis()+"::SeqNum=="+seqNum);
//        if (vedioData!=null&&vedioData.length>0) {
//            receiveVedioMap.put(seqNum, vedioData); //注意这里把标号设置为正整数了
//        }
//    }
//
//    /**
//     * 进行组包播放的正式处理
//     * 这是最主要的处理过程
//     */
//    @Override
//    public void run() {
//        //初始化运行参数
//        stopFlag=false;
//        int frequency=8000;
//        int channelConfiguration=AudioFormat.CHANNEL_CONFIGURATION_MONO; //通道配置
//        int audioEncoding=AudioFormat.ENCODING_PCM_16BIT; //编码格式
//        int bufferSize=AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
//        audioTrack=new AudioTrack(AudioManager.STREAM_MUSIC, frequency, channelConfiguration, audioEncoding, bufferSize, AudioTrack.MODE_STREAM);
//
//        long sleepMS=packMILLISECONDS/4; //计算睡眠时间，这是每次循环所间隔的周期，以一个包时长的4分之一进行计算
//        long expiredTime=expiredT>=0?expiredT:expiredP*packMILLISECONDS;
//
//        long _offsetTime=0; //从开始播放开始，到现在的流逝时间长度，毫秒
//        int _offsetNum=0; //对于当前包的向后调整偏移量
//        int _curPlayNum=0; //当前播放数据前进的指针
//        int _tolerateCount=0;//调整了几次容忍
//
//        byte[] _tempPlayData; //当前数据
//        byte[] _nullData=null;
//        long _beginTime=System.currentTimeMillis(); //设置开始说话时间，这个很重要**
//
//        initFlag=1; //初始化终于结束了，并且处理线程也已经启动了，此时在Manage中，才把这个对象放入Map中
//
//        //1、第一次说话控制
//        long _firstDelayTime=firstDelayT>=0?firstDelayT:firstDelayP*packMILLISECONDS;
//        while (!stopFlag&&!tobeStop) {
//            if ((continuePackOk(_curPlayNum, firstContinuePack))||((System.currentTimeMillis()-_beginTime>_firstDelayTime)&&_firstDelayTime>=0)) break;
//            try { sleep(sleepMS); } catch (InterruptedException e) {};
//        }
//
//        //2、正常播放开始了
//        audioTrack.play(); //注意这时才开始播放
//        int n=0;
//        String logStr="";
//        logStr=System.currentTimeMillis()+"::"+_beginTime+"::缓冲组包所用时间"+(System.currentTimeMillis()-_beginTime)+"::"+mapStatus();
//        logQueue.add(logStr);
//        _beginTime=System.currentTimeMillis();
////        //2.1、第一次播放
////        _tempPlayData=receiveVedioMap.get(_curPlayNum);
////        if (_tempPlayData!=null) {
////            _nullData=new byte[_tempPlayData.length];
////            for (int i=0; i<_nullData.length; i++) {
////                _nullData[i]=0;
////            }
////        }
////        while (_tempPlayData!=null) {
////            audioTrack.write(_tempPlayData, 0, _tempPlayData.length);
////            _tempPlayData=receiveVedioMap.get(++_curPlayNum);
////            {
////                logStr="("+n+")"+talkId+"::["+(_curPlayNum-1)+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+(_curPlayNum*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
////                logQueue.add(logStr);
////            }
////        }
//        _offsetTime=(System.currentTimeMillis()-_beginTime)-(_curPlayNum*packMILLISECONDS+interTolerateT*_tolerateCount);//计算偏移时间
//        //2、缓冲播放
//        while (!stopFlag&&!tobeStop) {
//            {
//                logStr="("+n+"-begin)"+talkId+"::["+_curPlayNum+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+(_curPlayNum*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
//                logQueue.add(logStr);
//            }
//            if (_offsetTime>-100) {
//                if (continuePackRate(_curPlayNum, cacheSize)==1) {
//                    for (int i=0; i<cacheSize; i++) {
//                        _tempPlayData=receiveVedioMap.get(_curPlayNum+i);
//                        audioTrack.write(_tempPlayData, 0, _tempPlayData.length);
//                        {
//                            logStr="("+n+"-readOneCache)"+talkId+"::["+(_curPlayNum+i)+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+((_curPlayNum+i)*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
//                            logQueue.add(logStr);
//                        }
//                    }
//                } else {
//                    try { sleep(interTolerateT); } catch (InterruptedException e) {}; //睡缓冲时间
//                    for (int i=0; i<cacheSize; i++) {
//                        _tempPlayData=receiveVedioMap.get(_curPlayNum+i);
//                        if (_tempPlayData!=null) {
//                            audioTrack.write(_tempPlayData, 0, _tempPlayData.length);
//                            {
//                                logStr="("+n+"-tolerateHAVE)"+talkId+"::["+(_curPlayNum+i)+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+((_curPlayNum+i)*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
//                                logQueue.add(logStr);
//                            }
//                        } else {
////                            audioTrack.write(_nullData, 0, _nullData.length);
//                            {
//                                logStr="("+n+"-tolerateNULL)"+talkId+"::["+(_curPlayNum+i)+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+((_curPlayNum+i)*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
//                                logQueue.add(logStr);
//                            }
//                        }
//                    }
//                    _tolerateCount++;
//                }
//            }
//            //判断是否不应该播放了：播放指针是否到了最后，多长时间还没有收到数据包了，是否停止了
//            if (tobeStop||(_curPlayNum>endNum&&endNum>0)||_offsetTime>expiredTime) {
//                {
//                    logStr="("+n+"-break)"+talkId+"::["+_curPlayNum+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+(_curPlayNum*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
//                    logQueue.add(logStr);
//                }
//                break;
//            }
//            _offsetTime=(System.currentTimeMillis()-_beginTime)-(_curPlayNum*packMILLISECONDS+interTolerateT*_tolerateCount);//计算偏移时间
//            {
//                logStr="("+n+"-oneCacheReaded)"+talkId+"::["+_curPlayNum+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+((_curPlayNum+i)*packMILLISECONDS+interTolerateT*_tolerateCount)+"]::"+mapStatus();
//                logQueue.add(logStr);
//            }
//            _curPlayNum+=cacheSize;
//            n++;
//            try { sleep(sleepMS); } catch (InterruptedException e) {}; //睡一会儿
//        }
//        Log.e("结束了===============","共调整过指针"+n+"次");
//        //结束运行
//        audioTrack.stop();
//        codec.deInitDecoder();
//        audioTrack.release();
//        audioTrack=null;
//        receiveVedioMap.clear();
//        receiveVedioMap=null;
//        stopFlag=true;
//
//        {
//            logStr="(END)"+talkId+"::["+_curPlayNum+"::"+System.currentTimeMillis()+"]::["+((System.currentTimeMillis()-_beginTime))+"::"+(_curPlayNum*packMILLISECONDS)+"]::"+mapStatus();
//            logQueue.add(logStr);
//        }
//        while (logQueue.size()>0) {
//            try { sleep(sleepMS); } catch (InterruptedException e) {}; //睡一会儿
//        }
//        try {
//            wl.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    /*
//     * 解压缩过程
//     * @param b 未解压前的字节数组
//     * @return 返回解压后的字节数组
//     */
//    private byte[] uncompress(byte[]  b) {
//        int i=0, j=0, k=0, _offset=0;
//        byte[] compressSegment = null;//未解压的长度为10的字节数组
//        short[] uncompressSegment;//解压后的单个数据,对应orig的解压结果
//        short[] _uncomproess=new short[b.length*8];//解压后组装好的
//        for (; i<b.length; i++) {
//            if (j==0) compressSegment=new byte[10]; //初始化未解压字节数组
//            compressSegment[j]=b[i];
//            j++;
//            if (j==10) {
//                uncompressSegment=new short[80]; //初始化解压后双字节数组
//                k=codec.decode(compressSegment, uncompressSegment); //解压
//                for (int m=0; m<k; m++) _uncomproess[_offset++]=uncompressSegment[m];
//                j=0;
//            }
//        }
//        //生成最终真实的解压数据，主要是截取
//        short[] _un=new short[_offset];
//        for(i=0; i<_offset; i++) _un[i]=_uncomproess[i];
//
//        return BytesTransUtils.getInstance().Shorts2Bytes(_un);
//    }
//
//    /*
//     * 从beginP处开始，是否有连续的continueNum个包存在
//     */
//    private boolean continuePackOk(int beginP, int continueNum) {
//        if (receiveVedioMap==null||receiveVedioMap.isEmpty()) return false;
//        for (int i=beginP; i<continueNum; i++) if (receiveVedioMap.get(i)==null) return false;
//        return true;
//    }
//
//    /*
//     * 从beginP处开始，到continueNum个包，填满率
//     */
//    private float continuePackRate(int beginP, int continueNum) {
//        if (receiveVedioMap==null||receiveVedioMap.isEmpty()) return 0;
//        int fillCount=0;
//        for (int i=beginP; i<continueNum; i++) if (receiveVedioMap.get(i)==null) fillCount++;
//        return (float)((float)fillCount/(float)continueNum);
//    }
//
//    /*
//     * 把有声音包的Map标号按顺序变为字符串，用户打印内存状态，调试时才有作用
//     */
//    private String mapStatus() {
//        String hasDataNum="empty map";
//        if (receiveVedioMap!=null) {
//            hasDataNum="";
//            for (int p=0; p<=maxNum; p++) {
//                if (receiveVedioMap.get(p)!=null) hasDataNum+=","+p;
//            }
//            hasDataNum=hasDataNum.length()>0?hasDataNum.substring(1):hasDataNum;
//        }
//        return hasDataNum;
//    }
//
//    private class WriteReceive extends Thread {
//        public void run() { 
//            while (true) {
//                try {
//                    String msg=timeMsgQueue.poll();
//                    if(msg!=null&&msg.trim().length()>0){
//
//                        //                        TalkId==f059b43f::Rtime==1467609629053::SeqNum==56
//                        //                        String message="TalkId=="+id+"::SeqNum"+SeqNum+"::Rtime=="+System.currentTimeMillis();
//                        if (msg.indexOf("TalkId")>=0) {
//                            String filePath= Environment.getExternalStorageDirectory() + "/woting/receivelogTK_player/";
//                            File dir=new File(filePath);
//                            if (!dir.isDirectory()) dir.mkdirs();
//                            filePath+=msg.substring(msg.indexOf("TalkId")+8, msg.indexOf("TalkId")+16);
//                            File f=new File(filePath);
//                            if (!f.exists()) f.createNewFile();
//                            String _sn=msg;
//                            FileWriter fw = null;
//                            try {
//                                fw = new FileWriter(f, true);
//                                fw.write(_sn+"\n");
//                                fw.flush();
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }finally{
//                                try {
//                                    fw.close();
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//                        }
//                    }
//                } catch(Exception e) {
//                    Log.e("talkplayer日志打印错误:::", e.toString());
//                }
//                try { sleep(20); } catch (InterruptedException e) {}
//            }
//        }
//    }
//
//    class WriteLog extends Thread {
//        public void run() {
//            String filePath=Environment.getExternalStorageDirectory() + "/woting/writelog/";
//            File dir=new File(filePath);
//            if (!dir.isDirectory()) dir.mkdirs();
//            filePath+=talkId;
//            File f=new File(filePath);
//            if (!f.exists())
//                try {
//                    f.createNewFile();
//                    FileWriter fw = null;
//                    try {
//                        fw = new FileWriter(f, true);
//                        while(true) {
//                            filePath=logQueue.take();
//                            if (filePath!=null&&!filePath.equals("")) fw.write(filePath+"\n");
//                            fw.flush();
//                            try { sleep(20); } catch (InterruptedException e) {}
//                        }
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }finally{
//                        try {
//                            fw.close();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                } catch (IOException e1) {
//                    e1.printStackTrace();
//                    Log.e("创建文件", e1.toString());
//                }
//        }
//    }
//
//}