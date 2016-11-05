package com.woting.audioSNS.calling.monitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.passport.UGA.service.UserService;
import com.woting.push.core.mem.TcpGlobalMemory;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

/**
 * 处理电话消息，包括把电话消息分发到每一个具体的处理线程
 * @author wanghui
 */
public class DealCalling extends AbstractLoopMoniter<CallingConfig> {
    private TcpGlobalMemory globalMem=TcpGlobalMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();

    private SessionService sessionService=null;
    private UserService userService=null;

    public DealCalling(CallingConfig cc, int index) {
        super(cc);
        super.setName("电话消息处理线程"+index);
        this.setLoopDelay(10);
    }

    @Override
    public boolean initServer() {
        sessionService=(SessionService)SpringShell.getBean("sessionService");
        userService=(UserService)SpringShell.getBean("userService");
        return sessionService!=null&&userService!=null;
    }

    @Override
    public void oneProcess() throws Exception {
        MsgNormal sourceMsg=(MsgNormal)globalMem.receiveMem.pollTypeMsg("2");
        if (sourceMsg==null) return;

        MsgNormal retMsg=buildRetMsg(sourceMsg);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        String callId=null;
        try {
            callId=((MapContent)sourceMsg.getMsgContent()).get("CallId")+"";
        } catch(Exception e) {}
        //不管任何消息，若CallId为空，则都认为是非法的消息，这类消息不进行任何处理，丢弃掉
        if (StringUtils.isNullOrEmptyOrSpace(callId)) return;

        OneCall oneCall=null;//通话对象
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(sourceMsg);
        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==1) {//发起呼叫过程
            String callerId=pUdk.getUserId();
            String CallederId=((MapContent)sourceMsg.getMsgContent()).get("CallederId")+"";
            //创建内存对象
            oneCall=new OneCall(1, callId, callerId, CallederId
                              , conf.get_ExpireOnline()
                              , conf.get_ExpireAck()
                              , conf.get_ExpireTime());
            oneCall.addPreMsg(sourceMsg);//设置第一个消息
            //加入内存
            int addFlag=callingMem.addOneCall(oneCall);
            if (addFlag!=1) {//返回错误信息
                retMsg.setReturnType(addFlag==0?0x81:0x82);
                globalMem.sendMem.addUserMsg(pUdk, retMsg);
                return;
            }
            //启动处理进程
            CallHandler callHandler=new CallHandler(conf, oneCall, sessionService, userService);
            callHandler.setDaemon(true);
            callHandler.start();
        } else {//其他消息，放到具体的独立处理线程中处理
            //查找是否有对应的内存数据，如果没有，则说明通话已经结束，告诉传来者
            oneCall=callingMem.getCallData(callId);
            if (oneCall==null) {//没有对应的内存数据
                retMsg.setCommand(0x30);
                retMsg.setMsgType(1);
                dataMap.put("HangupType", "0");
                dataMap.put("ServerMsg", "服务器处理进程不存在");
                MapContent mc=new MapContent(dataMap);
                retMsg.setMsgContent(mc);
                globalMem.sendMem.addUserMsg(pUdk, retMsg);
                return;
            }
            oneCall.addPreMsg(sourceMsg);//把消息压入队列
        }
    }

    @Override
    public void destroyServer() {
        //销毁所有消息处理线程
        List<CallHandler> chL=callingMem.getCallHanders();
        if (chL!=null&&!chL.isEmpty()) {
            for (CallHandler ch:chL) {
                ch.stopServer();
            }
            boolean allClosed=false;
            int i=0;
            while (i++<10&&!allClosed) {
                allClosed=true;
                for (CallHandler ch:chL) {
                    allClosed=ch.isStoped();
                    if (!allClosed) break;
                }
            }
        }
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    /*
     * 根据原消息，生成返回消息的壳 
     * @param msg
     * @return 返回消息壳
     */
    private MsgNormal buildRetMsg(MsgNormal msg) {
        MsgNormal retMsg=new MsgNormal();

        retMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        retMsg.setReMsgId(msg.getMsgId());
        retMsg.setToType(msg.getFromType());
        retMsg.setFromType(msg.getToType());
        retMsg.setMsgType(0);//是应答消息
        retMsg.setAffirm(0);//不需要回复
        retMsg.setBizType(msg.getBizType());
        retMsg.setCmdType(msg.getCmdType());

        return retMsg;
    }
}