package com.woting.push;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.jsonconf.JsonConfig;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.monitor.CleanCalling;
import com.woting.audioSNS.calling.monitor.DealCallingMsg;
import com.woting.audioSNS.intercom.IntercomConfig;
import com.woting.audioSNS.intercom.monitor.DealIntercomMsg;
import com.woting.audioSNS.mediaflow.MediaflowConfig;
import com.woting.audioSNS.mediaflow.monitor.DealMediaflowMsg;
import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.audioSNS.notify.monitor.DealNotifyMsg;
import com.woting.audioSNS.sync.SyncMessageConfig;
import com.woting.audioSNS.sync.monitor.DealSyncMsg;
import com.woting.push.core.SocketHandleConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.monitor.DispatchMessage;
import com.woting.push.core.service.LoadSysCacheService;
import com.woting.push.core.monitor.socket.nio.NioServer;
import com.woting.push.core.monitor.socket.oio.OioServer;
import com.woting.push.ext.SpringShell;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

/**
 * 服务启动的主类
 * @author wanghui
 */
public class ServerListener {
    private int socketType=0; //0=oio；1=nio

    public static void main(String[] args) {
        //处理参数，看是用nio还是用oio
        ServerListener sl=ServerListener.getInstance();
        try {
            Thread.currentThread().setName("推送服务主进程");
            sl.begin();
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        } catch(Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    //java的占位单例模式===begin
    private static ServerListener serverListener=null;
    private static class InstanceHolder {
        public static ServerListener instance=new ServerListener();
    }
    /**
     * 获得server的单例实例
     * @return
     */
    public static ServerListener getInstance() {
        if (serverListener==null) serverListener=InstanceHolder.instance;
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    private Logger logger=null;
    private static int _RUN_STATUS=0;//运行状态，0未启动，1正在启动，2启动成功；3准备停止；4停止

    private AbstractLoopMoniter<PushConfig> tcpCtlServer=null; //tcp控制信道监控服务
    private List<DispatchMessage> dispatchList=null; //分发线程的记录列表
    private List<DealIntercomMsg> dealIntercomList=null; //处理对讲消息线程的记录列表
    private List<DealCallingMsg> dealCallingList=null; //处理电话消息线程的记录列表
    private CleanCalling cleanCalling=null; //电话数据清理线程
    private List<DealMediaflowMsg> dealMediaFlowList=null; //处理媒体消息线程的记录列表
    private List<DealNotifyMsg> dealNotifyList=null; //处理通知消息线程的记录列表
    private List<DealSyncMsg> dealSyncList=null; //处理同步消息线程的记录列表

    /**
     * 获得运行状态
     */
    public int getRUN_STATUS() {
        return _RUN_STATUS;
    }

    /**
     * 停止服务
     */
    public void stop() {
        _RUN_STATUS=3;
    }

    //以下为Server的真实方法===========================================================================================
    /**
     * 初始化环境
     */
    private boolean initEnvironment() {
        boolean initOk=false;
        long beginTime=System.currentTimeMillis();
        long segmentBeginTime=beginTime;
        long _begin=System.currentTimeMillis();

        _RUN_STATUS=1;//==================正在启动

        //1=获取运行路径
        String rootPath=ServerListener.class.getResource("").getPath();
        if (rootPath.indexOf("!")!=-1) {//jar包
            rootPath=rootPath.substring(0, rootPath.indexOf("!"));
            String[] _s=rootPath.split("/");
            if (_s.length>1) {
                rootPath="/";
                for (int i=0; i<_s.length-1; i++) {
                    if (_s[i].equals("file:")) continue;
                    if (_s[i].length()>0) rootPath+=_s[i]+"/";
                }
            }
        } else {//class
            rootPath=rootPath.substring(0, rootPath.length()-"com.woting.push".length()-1);
            String[] _s=rootPath.split("/");
            if (_s.length>1) {
                rootPath="/";
                for (int i=0; i<_s.length-1; i++) if (_s[i].length()>0) rootPath+=_s[i]+"/";
            }
        }
        String os=System.getProperty("os.name");
        if (os.toLowerCase().startsWith("linux")||os.toLowerCase().startsWith("unix")||os.toLowerCase().startsWith("aix")) rootPath+="/";
        else if (os.toLowerCase().startsWith("window")&&rootPath.startsWith("/")) rootPath=rootPath.substring(1);
        SystemCache.setCache(new CacheEle<String>(PushConstants.APP_PATH, "系统运行的路径", rootPath));

        //2=logback加载xml内容
        LoggerContext lc=(LoggerContext)LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator=new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        try {
            String logConfFileName="logback-log";
            if (os.toLowerCase().startsWith("linux")||os.toLowerCase().startsWith("unix")||os.toLowerCase().startsWith("aix")) logConfFileName="conf/"+logConfFileName+"-linux.xml";
            else if (os.toLowerCase().startsWith("window")) logConfFileName=rootPath+"conf/"+logConfFileName+"-window.xml";
            configurator.doConfigure(logConfFileName);
        } catch (JoranException e) {
            e.printStackTrace();
        }
        try {
            logger=LoggerFactory.getLogger(ServerListener.class);
            logger.info("启动Push服务:一、环境加载=======================");
            logger.info("一、环境加载");
            logger.info("加载运行目录，用时[{}]毫秒", System.currentTimeMillis()-_begin);
            //读取系统配置
            _begin=System.currentTimeMillis();
            loadConfig(rootPath+"conf/config.jconf");
            logger.info("加载系统配置，用时[{}]毫秒", System.currentTimeMillis()-_begin);
            //Spring环境加载
            SpringShell.init();
            logger.info("加载Spring配置，用时[{}]毫秒", System.currentTimeMillis()-_begin);
            logger.info("环境加载结束，共用时[{}]毫秒", System.currentTimeMillis()-segmentBeginTime);

            logger.info("二、系统数据加载");
            segmentBeginTime=System.currentTimeMillis();
            //系统缓存数据加载
            _begin=System.currentTimeMillis();
            LoadSysCacheService loadSysCacheService=(LoadSysCacheService)SpringShell.getBean("loadSysCacheService");
            loadSysCacheService.loadCache();
            logger.info("加载系统缓存数据，用时[{}]毫秒", System.currentTimeMillis()-_begin);
            //初始化管理内存
            _begin=System.currentTimeMillis();
            PushGlobalMemory.getInstance();
            logger.info("初始化管理内存，用时[{}]毫秒", System.currentTimeMillis()-_begin);
            logger.info("系统数据加载结束，共用时[{}]毫秒", System.currentTimeMillis()-segmentBeginTime);

            initOk=true;
        } catch(Exception e) {
            logger.info("启动服务出现异常：\n{}", StringUtils.getAllMessage(e));
            initOk=false;
        }

        if (initOk) logger.info("Push服务环境加载成功，共用时[{}]毫秒", System.currentTimeMillis()-beginTime);
        else logger.info("Push服务环境加载错误，启动失败。共用时[{}]毫秒", System.currentTimeMillis()-beginTime);
        logger.info("启动Push服务:环境加载=[完成]======================");
        return initOk;
    }

    /*
     * 加载服务配置
     * @param configFileName 配置文件
     * @throws IOException
     */
    private void loadConfig(String configFileName) throws IOException {
        JsonConfig jc=new JsonConfig(configFileName);
        logger.info("配置文件信息={}", jc.getAllConfInfo());

        PushConfig pc=ConfigLoadUtils.getPushConfig(jc);
        SystemCache.setCache(new CacheEle<PushConfig>(PushConstants.PUSH_CONF, "系统配置", pc));

        SocketHandleConfig shc=ConfigLoadUtils.getSocketHandleConfig(jc);
        SystemCache.setCache(new CacheEle<SocketHandleConfig>(PushConstants.SOCKETHANDLE_CONF, "Socket处理配置", shc));

        IntercomConfig ic=ConfigLoadUtils.getIntercomConfig(jc);
        SystemCache.setCache(new CacheEle<IntercomConfig>(PushConstants.INTERCOM_CONF, "对讲控制配置", ic));

        CallingConfig cc=ConfigLoadUtils.getCallingConfig(jc);
        SystemCache.setCache(new CacheEle<CallingConfig>(PushConstants.CALLING_CONF, "电话控制配置", cc));

        MediaflowConfig mfc=ConfigLoadUtils.getMediaFlowConfig(jc);
        SystemCache.setCache(new CacheEle<MediaflowConfig>(PushConstants.MEDIAFLOW_CONF, "语音控制配置", mfc));

        NotifyMessageConfig nmc=ConfigLoadUtils.getNotifyMessageConfig(jc);
        SystemCache.setCache(new CacheEle<NotifyMessageConfig>(PushConstants.NOTIFY_CONF, "通知消息控制配置", nmc));

        SyncMessageConfig smc=ConfigLoadUtils.getSyncMessageConfig(jc);
        SystemCache.setCache(new CacheEle<SyncMessageConfig>(PushConstants.SYNC_CONF, "同步消息控制配置", smc));
    }

    private void begin() {
        //结束服务的钩子
        final Thread mainT=Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run() {
                logger.info("正在正在关闭服务... ");
                stopServers();
                try{
                    mainT.join();
                    logger.info("服务已关闭");
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });

        boolean initOk=initEnvironment();

        if (initOk) {
            startServers();//开始运行子进程
            listener();
            stopServers(); //若listener不结束，不会执行这里的内容
        }
    }

    @SuppressWarnings("unchecked")
    private void startServers() {
        logger.info("启动Push服务:二、监控服务启动=======================");
        //1-启动{TCP_控制信道}socket监控
        PushConfig pc=((CacheEle<PushConfig>)SystemCache.getCache(PushConstants.PUSH_CONF)).getContent();
        if (socketType==0) {
            tcpCtlServer=new OioServer(pc);
        } else {
            tcpCtlServer=new NioServer(pc);
        }
        tcpCtlServer.setDaemon(true);
        tcpCtlServer.start();
        //2-启动{接收消息分发}线程
        dispatchList=new ArrayList<DispatchMessage>();
        for (int i=0;i<pc.get_DispatchThreadCount(); i++) {
            DispatchMessage dm=new DispatchMessage(pc, i);
            dm.setDaemon(true);
            dm.start();
            dispatchList.add(dm);
        }
        //3-启动{处理对讲消息}线程
        IntercomConfig ic=((CacheEle<IntercomConfig>)SystemCache.getCache(PushConstants.INTERCOM_CONF)).getContent();
        dealIntercomList=new ArrayList<DealIntercomMsg>();
        for (int i=0;i<ic.get_DealThreadCount(); i++) {
            DealIntercomMsg di=new DealIntercomMsg(ic, i);
            di.setDaemon(true);
            di.start();
            dealIntercomList.add(di);
        }
        //4-启动{处理电话消息}线程
        CallingConfig cc=((CacheEle<CallingConfig>)SystemCache.getCache(PushConstants.CALLING_CONF)).getContent();
        dealCallingList=new ArrayList<DealCallingMsg>();
        for (int i=0;i<cc.get_DealThreadCount(); i++) {
            DealCallingMsg dc=new DealCallingMsg(cc, i);
            dc.setDaemon(true);
            dc.start();
            dealCallingList.add(dc);
        }
        //4.1-启动{电话清理任务}线程
        cleanCalling=new CleanCalling(cc);
        cleanCalling.setDaemon(true);
        cleanCalling.start();
        //5-启动{流数据处理}线程
        MediaflowConfig mfc=((CacheEle<MediaflowConfig>)SystemCache.getCache(PushConstants.MEDIAFLOW_CONF)).getContent();
        dealMediaFlowList=new ArrayList<DealMediaflowMsg>();
        for (int i=0;i<mfc.get_DealThreadCount(); i++) {
            DealMediaflowMsg dmf=new DealMediaflowMsg(mfc, i);
            dmf.setDaemon(true);
            dmf.start();
            dealMediaFlowList.add(dmf);
        }
        //6-启动{通知消息处理}线程
        NotifyMessageConfig nmc=((CacheEle<NotifyMessageConfig>)SystemCache.getCache(PushConstants.NOTIFY_CONF)).getContent();
        dealNotifyList=new ArrayList<DealNotifyMsg>();
        for (int i=0;i<nmc.get_DealThreadCount(); i++) {
            DealNotifyMsg dn=new DealNotifyMsg(nmc, i);
            dn.setDaemon(true);
            dn.start();
            dealNotifyList.add(dn);
        }
        //7-启动{同步消息处理}线程
        SyncMessageConfig smc=((CacheEle<SyncMessageConfig>)SystemCache.getCache(PushConstants.SYNC_CONF)).getContent();
        dealSyncList=new ArrayList<DealSyncMsg>();
        for (int i=0;i<smc.get_DealThreadCount(); i++) {
            DealSyncMsg ds=new DealSyncMsg(smc, i);
            ds.setDaemon(true);
            ds.start();
            dealSyncList.add(ds);
        }
        _RUN_STATUS=2;//==================启动成功
    }
    private void listener() {
        while (_RUN_STATUS==2) {
            ;
        }
    }
    private void stopServers() {
//        boolean allClosed=false;
//        int i=0;
        //1-停止{TCP_控制信道}socket监控
        if (tcpCtlServer!=null) {
            tcpCtlServer.stopServer();
//            i=0;
//            while (!tcpCtlServer.isStoped()&&i++<10) {
//                try { Thread.sleep(50); } catch(Exception e) {}
//            }
        }
        //2-停止{接收消息分发}线程
        if (dispatchList!=null&&!dispatchList.isEmpty()) {
            for (DispatchMessage dm: dispatchList) dm.stopServer();
//            while (!allClosed&&i++<10) {
//                allClosed=true;
//                for (DispatchMessage dm: dispatchList) {
//                    allClosed=dm.isStoped();
//                    if (!allClosed) break;
//                }
//                try { Thread.sleep(50); } catch(Exception e) {}
//            }
        }
        //3-停止{处理电话消息}线程
        if (dealIntercomList!=null&&!dealIntercomList.isEmpty()) {
            for (DealIntercomMsg di: dealIntercomList) di.stopServer();
//            while (!allClosed&&i++<10) {
//                allClosed=true;
//                for (DealIntercom di: dealIntercomList) {
//                    allClosed=di.isStoped();
//                    if (!allClosed) break;
//                }
//                try { Thread.sleep(50); } catch(Exception e) {}
//            }
        }
        //4-停止{处理电话消息}线程
        if (dealCallingList!=null&&!dealCallingList.isEmpty()) {
            for (DealCallingMsg dc: dealCallingList) dc.stopServer();
//            while (!allClosed&&i++<10) {
//                allClosed=true;
//                for (DealCalling dc: dealCallingList) {
//                    allClosed=dc.isStoped();
//                    if (!allClosed) break;
//                }
//                try { Thread.sleep(50); } catch(Exception e) {}
//            }
        }
        //4.1-停止{电话清理任务}线程
        if (cleanCalling!=null) {
            cleanCalling.stopServer();
//            i=0;
//            while (!cleanCalling.isStoped()&&i++<10) {
//                try { Thread.sleep(50); } catch(Exception e) {}
//            }
        }
        //5-停止{流数据处理}线程
        if (dealMediaFlowList!=null&&!dealMediaFlowList.isEmpty()) {
            for (DealMediaflowMsg dmf: dealMediaFlowList) dmf.stopServer();
//            while (!allClosed&&i++<10) {
//                allClosed=true;
//                for (DealMediaflow dmf: dealMediaFlowList) {
//                    allClosed=dmf.isStoped();
//                    if (!allClosed) break;
//                }
//                try { Thread.sleep(50); } catch(Exception e) {}
//            }
        }
        //6-停止{通知消息处理}线程
        if (dealNotifyList!=null&&!dealNotifyList.isEmpty()) {
            for (DealNotifyMsg dn: dealNotifyList) dn.stopServer();
        }
        //7-停止{同步消息处理}线程
        if (dealSyncList!=null&&!dealSyncList.isEmpty()) {
            for (DealSyncMsg ds: dealSyncList) ds.stopServer();
        }
        _RUN_STATUS=4;//==================成功停止
    }
}