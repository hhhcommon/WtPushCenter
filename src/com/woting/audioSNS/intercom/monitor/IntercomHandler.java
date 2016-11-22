package com.woting.audioSNS.intercom.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woting.audioSNS.intercom.IntercomConfig;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.push.core.mem.TcpGlobalMemory;
import com.woting.push.core.monitor.AbstractLoopMoniter;

public class IntercomHandler extends AbstractLoopMoniter<IntercomConfig> {
    private Logger logger=LoggerFactory.getLogger(IntercomHandler.class);

    private TcpGlobalMemory globalMem=TcpGlobalMemory.getInstance();
    private IntercomMemory interMem=IntercomMemory.getInstance();

    private OneMeet meetData=null;
    private volatile Object shutdownLock=new Object();

    protected IntercomHandler(IntercomConfig conf, OneMeet om) {
        super(conf);
        super.setName("组对讲处理["+om.getGroupId()+"]监控主线程");
        setLoopDelay(10);
        meetData=om;
        meetData.setIntercomHandler(this);
    }

    @Override
    public void oneProcess() throws Exception {
        try {
            //首先判断，是否可以继续通话
            int _s=meetData.getStatus();
            if (_s==9||_s==4) shutdown();//结束进程

            //一段时间后未收到任何消息，通话过期
            if ((meetData.getStatus()==1||meetData.getStatus()==2||meetData.getStatus()==3)
              &&(System.currentTimeMillis()-meetData.getLastTalkTime()>conf.get_ExpireTime()))
            {
                shutdown();
            }
        } catch(Exception e) {
            
        }
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    //=====以下三个为清除和关闭的操作
    //关闭
    private void shutdown() {
        if (meetData.getStatus()<9) {
            synchronized(shutdownLock) {
                meetData.setStatus_9();
                logger.debug("结束进程后1==[callid="+meetData.getGroupId()+"]:status="+meetData.getStatus());
                cleanData();
            }
        }
    }

    //清除数据，把本对讲控制的数据从内存数据链中移除
    private void cleanData() {
        logger.debug("结束进程后2==[callid="+meetData.getGroupId()+"]:status="+meetData.getStatus());
        //清除未发送消息
        globalMem.sendMem.cleanMsg4Intercom(meetData); //清除本对讲所涉及的未发出的消息
        meetData.clear();
        interMem.removeOneMeet(meetData.getGroupId());
    }
}