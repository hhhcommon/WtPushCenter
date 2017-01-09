package com.woting.audioSNS.calling.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MessageUtils;
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
public class DealCallingMsg extends AbstractLoopMoniter<CallingConfig> {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();

    private SessionService sessionService=null;

    public DealCallingMsg(CallingConfig cc, int index) {
        super(cc);
        super.setName("电话消息处理线程"+index);
        //this.setLoopDelay(10);
    }

    @Override
    public boolean initServer() {
        sessionService=(SessionService)SpringShell.getBean("sessionService");
        return sessionService!=null;
    }

    @Override
    public void oneProcess() throws Exception {
        MsgNormal sourceMsg=(MsgNormal)globalMem.receiveMem.takeTypeMsg("2");
        if (sourceMsg==null) return;

        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(sourceMsg);
        MsgNormal retMsg=MessageUtils.buildRetMsg(sourceMsg);

        //进入处理
        if (sourceMsg.getCmdType()==3&&sourceMsg.getCommand()==0) {
            List<Map<String, Object>> clm=callingMem.getActiveCallingList(pUdk.getUserId());
            if (clm!=null&&!clm.isEmpty()) {
                retMsg.setFromType(1);
                retMsg.setToType(0);
                retMsg.setCmdType(3);
                retMsg.setCommand(0);
                Map<String, Object> dataMap=new HashMap<String, Object>();
                if (clm==null||clm.isEmpty()) retMsg.setReturnType(0);
                else {
                    retMsg.setReturnType(1);
                    List<Map<String, Object>> callingList=new ArrayList<Map<String, Object>>();
                    for (Map<String, Object> ci: clm) {
                        UserPo caller=globalMem.uANDgMem.getUserById(ci.get("CallerId")+"");
                        UserPo calleder=globalMem.uANDgMem.getUserById(ci.get("CallederId")+"");
                        Map<String, Object> cm=new HashMap<String, Object>();
                        cm.put("CallId", ci.get("CallId"));
                        cm.put("CallerId", ci.get("CallerId"));
                        cm.put("CallederId", ci.get("CallederId"));
                        if (caller!=null) cm.put("CallerInfo", caller.toHashMap4View());
                        if (calleder!=null) cm.put("CallederInfo", calleder.toHashMap4View());
                        callingList.add(cm);
                    }
                    dataMap.put("CallingList", callingList);
                    MapContent mc=new MapContent(dataMap);
                    retMsg.setMsgContent(mc);
                }
                globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
            }
            return ;
        }

        String callId=null;
        try {
            callId=((MapContent)sourceMsg.getMsgContent()).get("CallId")+"";
        } catch(Exception e) {}
        //不管任何消息，若CallId为空，则都认为是非法的消息，这类消息不进行任何处理，丢弃掉
        if (StringUtils.isNullOrEmptyOrSpace(callId)) return;

        OneCall oneCall=callingMem.getOneCall(callId);//通话对象
        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==1) {//发起呼叫过程
            String callerId=pUdk.getUserId();
            String callederId=((MapContent)sourceMsg.getMsgContent()).get("CallederId")+"";
            if (oneCall==null) {
                //创建内存对象
                oneCall=new OneCall(1, callId, callerId, callederId);
                oneCall.putPreMsg(sourceMsg);//设置第一个消息
                //加入内存
                int addFlag=callingMem.addOneCall(oneCall);
                if (addFlag!=1) {//返回错误信息
                    Map<String, Object> dataMap=new HashMap<String, Object>();
                    dataMap.put("CallId", callId);
                    dataMap.put("CallerId", callerId);
                    dataMap.put("CallederId", callederId);
                    MapContent mc=new MapContent(dataMap);
                    retMsg.setMsgContent(mc);
                    retMsg.setReturnType(addFlag==0?0x81:0x82);
                    globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
                } else {
                    //启动处理进程
                    CallHandler callHandler=new CallHandler(conf, oneCall, sessionService);
                    callHandler.setDaemon(true);
                    callHandler.start();
                }
            } else { //告诉呼叫已经存在了，不能发起呼叫
                Map<String, Object> dataMap=new HashMap<String, Object>();
                dataMap.put("CallId", callId);
                dataMap.put("CallerId", callerId);
                dataMap.put("CallederId", callederId);
                MapContent mc=new MapContent(dataMap);
                retMsg.setMsgContent(mc);
                retMsg.setReturnType(0x83);
                globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
            }
        } else {//其他消息，放到具体的独立处理线程中处理
            //查找是否有对应的内存数据，如果没有，则说明通话已经结束，告诉传来者
            if (oneCall==null) {
                retMsg.setCmdType(3);
                retMsg.setCommand(0x30);
                retMsg.setReturnType(0x20);
                Map<String, Object> dataMap=new HashMap<String, Object>();
                dataMap.put("CallId", callId);
                dataMap.put("ServerMsg", "服务器处理进程不存在");
                MapContent mc=new MapContent(dataMap);
                retMsg.setMsgContent(mc);
                globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
            } else oneCall.putPreMsg(sourceMsg);//把消息压入队列
        }
    }

    @Override
    public void destroyServer() {
        //销毁所有消息处理线程
        List<CallHandler> chL=callingMem.getCallHanders();
        if (chL!=null&&!chL.isEmpty()) {
            for (CallHandler ch:chL) ch.stopServer();
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
}