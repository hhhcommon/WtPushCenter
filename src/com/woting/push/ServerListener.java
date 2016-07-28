package com.woting.push;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.jsonconf.JsonConfig;
import com.woting.push.config.ConfigLoadUtils;
import com.woting.push.config.PushConfig;
//import com.woting.push.core.monitor.TcpNioSocketServer;
import com.woting.push.core.monitor.TcpSocketServer;
import com.woting.push.core.service.LoadSysCacheService;
import com.woting.push.ext.SpringShell;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

public class ServerListener {
    public static void main(String[] args) {
        ServerListener sl = ServerListener.getInstance();
        try {
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

//    private TcpNioSocketServer  tcpCtlServer=null; //tcp控制信道监控服务
    private TcpSocketServer  tcpCtlServer=null; //tcp控制信道监控服务
    
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
    /*
     * 初始化环境
     */
    private void initEnvironment() {
        boolean initOk=false;
        long beginTime=System.currentTimeMillis();
        long segmentBeginTime=beginTime;
        long _begin=System.currentTimeMillis();

        _RUN_STATUS=1;//==================正在启动

        //获取运行路径
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
            rootPath=rootPath.substring(0, rootPath.length()-"com.woting.crawler".length()-1);
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

        //logback加载xml内容
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
            logger.info("启动Push服务=======================");
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
            logger.info("系统数据加载结束，共用时[{}]毫秒", System.currentTimeMillis()-segmentBeginTime);

            initOk=true;
        } catch(Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.info("启动服务出现异常：\n{}", sw.toString());
            initOk=false;
        }

        if (initOk) logger.info("Push服务环境加载成功，共用时[{}]毫秒", System.currentTimeMillis()-beginTime);
        else logger.info("Push服务环境加载错误，启动失败。共用时[{}]毫秒", System.currentTimeMillis()-beginTime);
    }
    private void loadConfig(String configFileName) throws IOException {
        JsonConfig jc=new JsonConfig(configFileName);
        PushConfig pc=ConfigLoadUtils.getPushConfig(jc);
        SystemCache.setCache(new CacheEle<PushConfig>(PushConstants.PUSH_CONF, "系统运行的路径", pc));
    }

    private void begin() {
        //结束服务的钩子
        final Thread mainT=Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run(){
                _RUN_STATUS=3;
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

        initEnvironment();

        //开始运行子进程
        startServers();
        listener();
        stopServers(); //若listener不结束，不会执行这里的内容
    }

    private void startServers() {
        //1-启动{TCP_控制信道}socket监控
        startTcpServer();//开启TCP服务，用于控制信息的传送
        _RUN_STATUS=2;//==================启动成功
    }
    private void listener() {
        while (_RUN_STATUS==2) {
            ;
        }
    }
    private void stopServers() {
        stopTcpServer();

        int i=0;
        while (tcpCtlServer.getRUN_STATUS()!=4&&(i++<10)) {
            try { Thread.sleep(500); } catch(Exception e) {}
        }
        tcpCtlServer.stop();
        _RUN_STATUS=4;//==================成功停止
    }

    //以下启动具体的服务===========================================================
    //1-tcp控制信道监控服务
    private void startTcpServer() {
        tcpCtlServer=new TcpSocketServer(((CacheEle<PushConfig>)SystemCache.getCache(PushConstants.PUSH_CONF)).getContent());
        tcpCtlServer.setDaemon(true);
        tcpCtlServer.start();
    }
    private void stopTcpServer() {
        if (tcpCtlServer!=null) tcpCtlServer.stopServer();
    }
}