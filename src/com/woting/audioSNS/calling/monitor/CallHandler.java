package com.woting.audioSNS.calling.monitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.ProcessedMsg;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.monitor.socket.oio.SocketHandler;
import com.woting.push.core.service.SessionService;
import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.push.user.PushUserUDKey;

/**
 * 电话控制线程
 * @author wanghui
 */
public class CallHandler extends AbstractLoopMoniter<CallingConfig> {
    private Logger logger=LoggerFactory.getLogger(CallHandler.class);

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();

    private OneCall callData=null;//所控制的通话数据
    private volatile Object shutdownLock=new Object();
    private boolean isCallederTalked=false; //是否“呼叫者”说话

    private SessionService sessionService=null;
    private Timer moniterTimer=null;

    /**
     * 构造函数，必须给定一个通话控制数据
     * @param callData
     */
    protected CallHandler(CallingConfig conf, OneCall oneCall, SessionService sessionService) {
        super(conf);
        super.setName("电话["+oneCall.getCallId()+"]消息处理线程");
        //setLoopDelay(10);
        callData=oneCall;
        this.sessionService=sessionService;
        callData.setCallHandler(this);
        //启动监控线程
        moniterTimer=new Timer("电话["+oneCall.getCallId()+"]监控线程", true);
        moniterTimer.scheduleAtFixedRate(new MonitorOneCall(), 0, conf.get_CleanInternal());
    }

    @Override
    public boolean canContinue() {
        return callData.getStatus()!=9;
    }

    @Override
    public void oneProcess() throws Exception {
        try {
            //读取预处理的消息
            MsgNormal m=callData.takePreMsg();//第一条必然是呼叫信息
            if (m==null||m.getMsgId().equals("-1")) return;

            callData.setLastUsedTime();
            int flag=1;
            ProcessedMsg pMsg=new ProcessedMsg(m, System.currentTimeMillis(), getClass().getName());
            try {
                if (m.getCmdType()==1) {
                    if (m.getCommand()==1) dial(m);//呼叫处理
                    else
                    if (m.getCommand()==0x90) flag=dealAutoDialFeedback(m); //“被叫者”的自动反馈
                    else
                    if (m.getCommand()==2) flag=ackDial(m); //“被叫者”的手工应答
                    else
                    if (m.getCommand()==3) hangup(m); //挂断通话
                }
                if (m.getCmdType()==2) {
                    if (m.getCommand()==1) beginPTT(m);//开始对讲
                    else
                    if (m.getCommand()==2) endPTT(m); //结束对讲
                }
                pMsg.setStatus(flag);
                //“呼叫者”第一次说话
                if (!isCallederTalked&&callData.getCallederWts().size()==1) {
                    dealCallerFirstTalk(callData.getCallederWts().get(0).getTalkerUdk());
                    isCallederTalked=true;
                }
            } catch(Exception e) {
                logger.debug(StringUtils.getAllMessage(e));
                pMsg.setStatus(-1);
            } finally {
                pMsg.setEndTime(System.currentTimeMillis());
                callData.addProcessedMsg(pMsg);
            }
        } catch(Exception e) {
            logger.debug(StringUtils.getAllMessage(e));
        }
    }

    //===========以下是分步处理过程，全部是私有函数
    //处理呼叫(CALL:1)
    private void dial(MsgNormal m) throws InterruptedException {
        callData.setBeginDialTime();

        callData.setCallerKey(PushUserUDKey.buildFromMsg(m));
        String callId =((MapContent)m.getMsgContent()).get("CallId")+"";
        String callerId=PushUserUDKey.buildFromMsg(m).getUserId();
        String callederId=((MapContent)m.getMsgContent()).get("CallederId")+"";

        //返回给呼叫者的消息
        Map<String, Object> dataMap=null;
        MsgNormal toCallerMsg=new MsgNormal();
        toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setReMsgId(m.getMsgId());
        toCallerMsg.setFromType(m.getToType());
        toCallerMsg.setToType(m.getFromType());
        toCallerMsg.setMsgType(1);
        toCallerMsg.setBizType(0x02);
        toCallerMsg.setCmdType(1);
        toCallerMsg.setCommand(0x09);
        toCallerMsg.setPCDType(0);
        dataMap=new HashMap<String, Object>();
        dataMap.put("CallId", callId);
        dataMap.put("CallerId", callerId);
        dataMap.put("CallederId", callederId);
        MapContent mc=new MapContent(dataMap);
        toCallerMsg.setMsgContent(mc);

        int returnType=0;
        SocketHandler _sh;
        if (callerId.equals(callederId)) returnType=6;
        //判断呼叫者是否存在
        if (returnType==0) {
            _sh=globalMem.getSocketByPushUser(callData.getCallerKey());
            if (_sh==null||!_sh.socketOk()) returnType=2;
        }
        //判断被叫者是否存在
        if (returnType==0) {
            List<PushUserUDKey> calleredKeys=(List<PushUserUDKey>)sessionService.getActivedUserUDKs(callederId);
            if (calleredKeys!=null&&!calleredKeys.isEmpty()) {
                for (PushUserUDKey udk: calleredKeys) {
                    _sh=globalMem.getSocketByPushUser(udk);
                    if (_sh!=null&&_sh.socketOk()) callData.addCallederList(udk);
                }
            }
            if (callData.getCallederList()==null||callData.getCallederList().isEmpty()) returnType=3;
        }
        //判断是否占线，不判断被叫者是否在对讲组中进行对讲，若在对讲也认为在线，并且不忙，是否接听，让被叫者处理
        if (returnType==0&&callingMem.isTalk(callederId, callId)) returnType=4;
        if (returnType==0) returnType=1;
        toCallerMsg.setReturnType(returnType);
        globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
        //记录到已发送列表
        callData.addSendedMsg(toCallerMsg);
        callingMem.addUserInCall(callerId, callData);

        //给被叫者发送信息
        if (returnType==1) {
            for (PushUserUDKey _pUdk: callData.getCallederList()) {
                MsgNormal toCallederMsg=new MsgNormal();
                toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                toCallederMsg.setMsgType(0);
                toCallederMsg.setAffirm(1);
                toCallederMsg.setBizType(2);
                toCallederMsg.setCmdType(1);
                toCallederMsg.setCommand(0x10);
                toCallederMsg.setFromType(1);
                toCallederMsg.setToType(0);
                dataMap=new HashMap<String, Object>();
                dataMap.put("DialType", returnType==4?"2":"1");
                dataMap.put("CallId", callId);
                dataMap.put("CallerId", callerId);
                dataMap.put("CallederId", callederId);
                MapContent _mc=new MapContent(dataMap);
                toCallederMsg.setMsgContent(_mc);
                //加入“呼叫者”的用户信息给被叫者
                Map<String, Object> callerInfo=new HashMap<String, Object>();
                UserPo u=globalMem.uANDgMem.getUserById(callerId);
                callerInfo.put("UserName", u.getLoginName());
                callerInfo.put("UserNum", u.getUserNum());
                callerInfo.put("Portrait", u.getPortraitMini());
                callerInfo.put("Mail", u.getMailAddress());
                callerInfo.put("Descn", u.getDescn());
                dataMap.put("CallerInfo", callerInfo);
                toCallederMsg.setPCDType(0);
                globalMem.sendMem.putDeviceMsg(_pUdk, toCallederMsg);
                //记录到已发送列表
                callData.addSendedMsg(toCallederMsg);
            }
        }

        if (returnType!=1) shutdown(); //若呼叫不成功，要删除数据及这个过程
        else callData.setStatus_1();   //若呼叫成功，修改状态
    }

    //处理“被叫者”的自动呼叫反馈(CALL:-b1)
    private int dealAutoDialFeedback(MsgNormal m) throws InterruptedException {
        if (callData.getStatus()==1) {//状态正确，如果是其他状态，这个消息抛弃
            //发送给呼叫者的消息
            MsgNormal toCallerMsg=new MsgNormal();
            toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
            toCallerMsg.setFromType(1);
            toCallerMsg.setToType(0);
            toCallerMsg.setMsgType(0);
            toCallerMsg.setAffirm(1);
            toCallerMsg.setBizType(2);
            toCallerMsg.setCmdType(1);
            toCallerMsg.setCommand(0x40);
            Map<String, Object> dataMap=new HashMap<String, Object>();
            dataMap.put("CallId", callData.getCallId());
            dataMap.put("CallerId", callData.getCallerId());
            dataMap.put("CallederId", callData.getCallederId());
            dataMap.put("OnLineType", "1");
            MapContent mc=new MapContent(dataMap);
            toCallerMsg.setMsgContent(mc);
            toCallerMsg.setPCDType(0);
            globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
            callData.addSendedMsg(toCallerMsg);//记录到已发送列表

            callData.setStatus_2();//修改状态，已收到“自动应答”
            return 1;
        } else return 2;//被抛弃
    }

    //处理“被叫者”应答(CALL:2)
    private int ackDial(MsgNormal m) throws InterruptedException {
        if (callData.getStatus()==1||callData.getStatus()==2) {//状态正确，如果是其他状态，这个消息抛弃
            PushUserUDKey ackUdkey=PushUserUDKey.buildFromMsg(m);
            callData.setCallederKey(ackUdkey);
            callingMem.addUserInCall(ackUdkey.getUserId(), callData);

            //应答状态
            int ackType=2; //拒绝
            try {
                ackType=Integer.parseInt(""+((MapContent)m.getMsgContent()).get("ACKType"));
            } catch(Exception e) {}

            //构造“应答传递ACK”消息，并发送给“呼叫者”
            MsgNormal toCallerMsg=new MsgNormal();
            toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
            toCallerMsg.setFromType(1);
            toCallerMsg.setToType(0);
            toCallerMsg.setMsgType(0);
            toCallerMsg.setAffirm(1);
            toCallerMsg.setBizType(2);
            toCallerMsg.setCmdType(1);
            toCallerMsg.setCommand(0x20);
            Map<String, Object> dataMap=new HashMap<String, Object>();
            dataMap.put("CallId", callData.getCallId());
            dataMap.put("CallerId", callData.getCallerId());
            dataMap.put("CallederId", callData.getCallederId());
            dataMap.put("ACKType", ackType);
            MapContent mc=new MapContent(dataMap);
            toCallerMsg.setMsgContent(mc);
            toCallerMsg.setPCDType(0);
            globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
            callData.addSendedMsg(toCallerMsg);//记录到已发送列表
            //告诉其他被叫设备
            for (PushUserUDKey _pUdkey: callData.getCallederList()) {
                if (!ackUdkey.equals(_pUdkey)) {
                    MsgNormal toOtherCallederMsg=new MsgNormal();
                    toOtherCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                    toOtherCallederMsg.setFromType(1);
                    toOtherCallederMsg.setToType(0);
                    toOtherCallederMsg.setMsgType(0);
                    toOtherCallederMsg.setAffirm(1);
                    toOtherCallederMsg.setBizType(2);
                    toOtherCallederMsg.setCmdType(1);
                    toOtherCallederMsg.setCommand(0x20);
                    Map<String, Object> _dataMap=new HashMap<String, Object>();
                    _dataMap.put("CallId", callData.getCallId());
                    _dataMap.put("CallerId", callData.getCallerId());
                    _dataMap.put("CallederId", callData.getCallederId());
                    _dataMap.put("ACKType", ackType);
                    MapContent _mc=new MapContent(_dataMap);
                    toOtherCallederMsg.setMsgContent(_mc);
                    toOtherCallederMsg.setPCDType(0);
                    globalMem.sendMem.putDeviceMsg(_pUdkey, toOtherCallederMsg);
                    callData.addSendedMsg(toOtherCallederMsg);
                }
            }

            if (ackType==1) callData.setStatus_3();//修改状态:正常通话
            else
            if (ackType==2||ackType==31) callData.setStatus_4();//修改状态:挂断
            return 1;
        } else return 2;//被抛弃
    }

    //处理“挂断”(CALL:3)
    private int hangup(MsgNormal m) throws InterruptedException {
        //首先判断是那方在进行挂断
        List<PushUserUDKey> _others=callData.getOthers(PushUserUDKey.buildFromMsg(m));
        if (_others==null||_others.isEmpty()) return 3;

        //给另一方发送“挂断传递”消息
        for (PushUserUDKey _pUdkey: _others) {
            MsgNormal otherMsg=new MsgNormal();
            otherMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
            otherMsg.setReMsgId(m.getMsgId());
            otherMsg.setFromType(1);
            otherMsg.setToType(0);
            otherMsg.setMsgType(1);
            otherMsg.setAffirm(0);
            otherMsg.setBizType(2);
            otherMsg.setCmdType(1);
            otherMsg.setCommand(0x30);
            Map<String, Object> dataMap=new HashMap<String, Object>();
            dataMap.put("CallId", callData.getCallId());
            dataMap.put("CallerId", callData.getCallerId());
            dataMap.put("CallederId", callData.getCallederId());
            dataMap.put("HangupType", "1");
            MapContent mc=new MapContent(dataMap);
            otherMsg.setMsgContent(mc);
            otherMsg.setPCDType(0);
            globalMem.sendMem.putDeviceMsg(_pUdkey, otherMsg);
            callData.addSendedMsg(otherMsg);//记录到已发送列表
        }
        
        callData.setStatus_4();//修改状态
        return 1;
    }

    //处理开始对讲(PTT:1)
    private void beginPTT(MsgNormal m) throws InterruptedException {
        MsgNormal toSpeakerMsg=new MsgNormal();
        toSpeakerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toSpeakerMsg.setFromType(1);
        toSpeakerMsg.setToType(0);
        toSpeakerMsg.setReMsgId(m.getMsgId());
        toSpeakerMsg.setMsgType(1);
        toSpeakerMsg.setAffirm(1);
        toSpeakerMsg.setBizType(2);
        toSpeakerMsg.setCmdType(2);
        toSpeakerMsg.setCommand(9);

        Map<String, Object> dataMap=new HashMap<String, Object>();

        PushUserUDKey speaker=PushUserUDKey.buildFromMsg(m);
        if (speaker==null) {
            toSpeakerMsg.setReturnType(0);
        } else {
            String ret=callData.setSpeaker(speaker);
            if (ret.equals("1")) {
                toSpeakerMsg.setReturnType(1);
            } else if (ret.startsWith("2::")) {
                toSpeakerMsg.setReturnType(2);
                dataMap.put("Speaker", ret.substring(3));
            } else if (ret.equals("0")) {
                toSpeakerMsg.setReturnType(3);
                dataMap.put("ErrMsg", "当前会话为非对讲模式，不用申请独占的通话资源");
            } else {
                dataMap.put("ErrMsg", ret.startsWith("-")?ret.substring(ret.indexOf("::")+2):"未知问题");
            }

        }
        dataMap.put("CallId", callData.getCallId());
        MapContent mc=new MapContent(dataMap);
        toSpeakerMsg.setMsgContent(mc);
        toSpeakerMsg.setPCDType(0);
        globalMem.sendMem.putDeviceMsg(speaker, toSpeakerMsg);
        callData.addSendedMsg(toSpeakerMsg);

        //告知对方，有人通话
        PushUserUDKey _other=callData.getOtherUdk(speaker.getUserId());
        if (_other!=null) {
            MsgNormal toOtherMsg=MessageUtils.clone(toSpeakerMsg);
            toOtherMsg.setReMsgId(null);
            toOtherMsg.setMsgType(0);
            toOtherMsg.setCommand(0x10);
            dataMap=new HashMap<String, Object>();
            dataMap.put("SpeakerId", speaker.getUserId());
            MapContent _mc=new MapContent(dataMap);
            toOtherMsg.setMsgContent(_mc);
            globalMem.sendMem.putDeviceMsg(_other, toOtherMsg);
            callData.addSendedMsg(toOtherMsg);
        }
    }

    //处理结束对讲(PTT:2)
    private void endPTT(MsgNormal m) throws InterruptedException {
        MsgNormal toSpeakerMsg=new MsgNormal();
        toSpeakerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toSpeakerMsg.setFromType(1);
        toSpeakerMsg.setToType(0);
        toSpeakerMsg.setReMsgId(m.getMsgId());
        toSpeakerMsg.setMsgType(1);
        toSpeakerMsg.setAffirm(1);
        toSpeakerMsg.setBizType(2);
        toSpeakerMsg.setCmdType(2);
        toSpeakerMsg.setCommand(0x0A);

        Map<String, Object> dataMap=new HashMap<String, Object>();

        PushUserUDKey speaker=PushUserUDKey.buildFromMsg(m);
        if (speaker==null) {
            toSpeakerMsg.setReturnType(0);
        } else {
            String ret=callData.releaseSpeaker(speaker);
            if (ret.equals("1")) {
                toSpeakerMsg.setReturnType(1);
            } else if (ret.equals("0")) {
                toSpeakerMsg.setReturnType(3);
                dataMap.put("ErrMsg", "当前会话为非对讲模式，不用申请独占的通话资源");
            } else if (ret.startsWith("2::")) {
                toSpeakerMsg.setReturnType(2);
                dataMap.put("Speaker", ret.substring(3));
            } else {
                dataMap.put("ErrMsg", ret.startsWith("-")?ret.substring(ret.indexOf("::")+2):"未知问题");
            }

        }
        dataMap.put("CallId", callData.getCallId());
        MapContent mc=new MapContent(dataMap);
        toSpeakerMsg.setMsgContent(mc);
        toSpeakerMsg.setPCDType(0);
        globalMem.sendMem.putDeviceMsg(speaker, toSpeakerMsg);
        callData.addSendedMsg(toSpeakerMsg);

        //告知对方，结束通话
        PushUserUDKey _other=callData.getOtherUdk(speaker.getUserId());
        if (_other!=null) {
            MsgNormal toOtherMsg=MessageUtils.clone(toSpeakerMsg);
            toOtherMsg.setReMsgId(null);
            toOtherMsg.setMsgType(0);
            toOtherMsg.setCommand(0x20);
            dataMap=new HashMap<String, Object>();
            dataMap.put("SpeakerId", speaker.getUserId());
            MapContent _mc=new MapContent(dataMap);
            toOtherMsg.setMsgContent(_mc);
            globalMem.sendMem.putDeviceMsg(_other, toOtherMsg);
            callData.addSendedMsg(toOtherMsg);
        }
    }

    //=======以下3个超时处理
    //处理“被叫者”不在线
    private void dealOutLine() throws InterruptedException {
        //发送给“呼叫者”的消息
        MsgNormal toCallerMsg=new MsgNormal();
        toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setFromType(1);
        toCallerMsg.setToType(0);
        toCallerMsg.setMsgType(0);
        toCallerMsg.setAffirm(1);
        toCallerMsg.setBizType(2);
        toCallerMsg.setCmdType(1);
        toCallerMsg.setCommand(0x40);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("CallId", callData.getCallId());
        dataMap.put("CallerId", callData.getCallerId());
        dataMap.put("CallederId", callData.getCallederId());
        dataMap.put("OnLineType", "2");
        MapContent mc=new MapContent(dataMap);
        toCallerMsg.setMsgContent(mc);
        toCallerMsg.setPCDType(0);
        globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
        callData.addSendedMsg(toCallerMsg);//记录到已发送列表
    }

    //处理“被叫者”未手工应答
    private void dealNoAck() throws InterruptedException {
        //1、构造“应答传递ACK”消息，并发送给“呼叫者”
        MsgNormal toCallerMsg=new MsgNormal();
        toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setFromType(1);
        toCallerMsg.setToType(0);
        toCallerMsg.setMsgType(0);
        toCallerMsg.setAffirm(1);
        toCallerMsg.setBizType(2);
        toCallerMsg.setCmdType(1);
        toCallerMsg.setCommand(0x20);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("CallId", callData.getCallId());
        dataMap.put("CallerId", callData.getCallerId());
        dataMap.put("CallederId", callData.getCallederId());
        dataMap.put("ACKType", "32");
        MapContent mc=new MapContent(dataMap);
        toCallerMsg.setMsgContent(mc);
        toCallerMsg.setPCDType(0);
        globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
        callData.addSendedMsg(toCallerMsg);//记录到已发送列表

        //2、构造“挂断传递”消息，并发送给“被叫者”
        //告诉其他被叫设备
        if (callData.getCallederKey()!=null) {
            MsgNormal toCallederMsg=new MsgNormal();
            toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
            toCallederMsg.setFromType(1);
            toCallederMsg.setToType(0);
            toCallederMsg.setMsgType(0);
            toCallederMsg.setAffirm(1);
            toCallederMsg.setBizType(2);
            toCallederMsg.setCmdType(1);
            toCallederMsg.setCommand(0x30);
            dataMap=new HashMap<String, Object>();
            dataMap.put("CallId", callData.getCallId());
            dataMap.put("CallerId", callData.getCallerId());
            dataMap.put("CallederId", callData.getCallederId());
            dataMap.put("HangupType", "2");
            MapContent _mc=new MapContent(dataMap);
            toCallederMsg.setMsgContent(_mc);
            toCallederMsg.setPCDType(0);
            globalMem.sendMem.putDeviceMsg(callData.getCallederKey(), toCallederMsg);
            callData.addSendedMsg(toCallederMsg);//记录到已发送列表
        } else {
            if (callData.getCallederList()!=null) {
                for (PushUserUDKey _pUdkey: callData.getCallederList()) {
                    MsgNormal toCallederMsg=new MsgNormal();
                    toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                    toCallederMsg.setFromType(1);
                    toCallederMsg.setToType(0);
                    toCallederMsg.setMsgType(0);
                    toCallederMsg.setAffirm(1);
                    toCallederMsg.setBizType(2);
                    toCallederMsg.setCmdType(1);
                    toCallederMsg.setCommand(0x30);
                    dataMap=new HashMap<String, Object>();
                    dataMap.put("CallId", callData.getCallId());
                    dataMap.put("CallerId", callData.getCallerId());
                    dataMap.put("CallederId", callData.getCallederId());
                    dataMap.put("HangupType", "2");
                    MapContent _mc=new MapContent(dataMap);
                    toCallederMsg.setMsgContent(_mc);
                    toCallederMsg.setPCDType(0);
                    globalMem.sendMem.putDeviceMsg(_pUdkey, toCallederMsg);
                    callData.addSendedMsg(toCallederMsg);//记录到已发送列表
                }
            }
        }
    }

    //服务器发现电话过程过期
    private void dealCallExpire() throws InterruptedException {
        //发送给“呼叫者”的消息
        MsgNormal toCallerMsg=new MsgNormal();
        toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setFromType(1);
        toCallerMsg.setToType(0);
        toCallerMsg.setMsgType(0);
        toCallerMsg.setAffirm(1);
        toCallerMsg.setBizType(2);
        toCallerMsg.setCmdType(1);
        toCallerMsg.setCommand(0x30);
        Map<String, Object> callerMap=new HashMap<String, Object>();
        callerMap.put("CallId", callData.getCallId());
        callerMap.put("CallerId", callData.getCallerId());
        callerMap.put("CallederId", callData.getCallederId());
        callerMap.put("HangupType", "3");
        MapContent mc=new MapContent(callerMap);
        toCallerMsg.setMsgContent(mc);
        toCallerMsg.setPCDType(0);
        globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
        callData.addSendedMsg(toCallerMsg);//记录到已发送列表

        //发送给“被叫者”的消息
        //告诉其他被叫设备
        Map<String, Object> callederMap=null;
        if (callData.getCallederKey()!=null) {
            MsgNormal toCallederMsg=new MsgNormal();
            toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
            toCallederMsg.setFromType(1);
            toCallederMsg.setToType(0);
            toCallederMsg.setMsgType(0);
            toCallederMsg.setAffirm(1);
            toCallederMsg.setBizType(2);
            toCallederMsg.setCmdType(1);
            toCallederMsg.setCommand(0x30);
            callederMap=new HashMap<String, Object>();
            callederMap.put("CallId", callData.getCallId());
            callederMap.put("CallerId", callData.getCallerId());
            callederMap.put("CallederId", callData.getCallederId());
            callederMap.put("HangupType", "3");
            MapContent _mc=new MapContent(callederMap);
            toCallederMsg.setMsgContent(_mc);
            toCallederMsg.setPCDType(0);
            globalMem.sendMem.putDeviceMsg(callData.getCallederKey(), toCallederMsg);
            callData.addSendedMsg(toCallederMsg);
        } else {
            if (callData.getCallederList()!=null) {
                for (PushUserUDKey _pUdkey: callData.getCallederList()) {
                    MsgNormal toCallederMsg=new MsgNormal();
                    toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                    toCallederMsg.setFromType(1);
                    toCallederMsg.setToType(0);
                    toCallederMsg.setMsgType(0);
                    toCallederMsg.setAffirm(1);
                    toCallederMsg.setBizType(2);
                    toCallederMsg.setCmdType(1);
                    toCallederMsg.setCommand(0x30);
                    callederMap=new HashMap<String, Object>();
                    callederMap.put("CallId", callData.getCallId());
                    callederMap.put("CallerId", callData.getCallerId());
                    callederMap.put("CallederId", callData.getCallederId());
                    callederMap.put("HangupType", "3");
                    MapContent _mc=new MapContent(callederMap);
                    toCallederMsg.setMsgContent(_mc);
                    toCallederMsg.setPCDType(0);
                    globalMem.sendMem.putDeviceMsg(_pUdkey, toCallederMsg);
                    callData.addSendedMsg(toCallederMsg);
                }
            }
        }
    }

    //===处理第一次被叫者说话的特殊流程
    private void  dealCallerFirstTalk(PushUserUDKey callederKey) throws InterruptedException {
        if (callData.getStatus()==1||callData.getStatus()==2) {//等同于“被叫者”手工应答
            callData.setCallederKey(callederKey);
            callingMem.addUserInCall(callederKey.getUserId(), callData);

            if (callData.getCallerKey()!=null) {
                //构造“应答传递ACK”消息，并发送给“呼叫者”
                MsgNormal toCallerMsg=new MsgNormal();
                toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                toCallerMsg.setFromType(1);
                toCallerMsg.setToType(0);
                toCallerMsg.setMsgType(0);
                toCallerMsg.setAffirm(0);
                toCallerMsg.setBizType(2);
                toCallerMsg.setCmdType(1);
                toCallerMsg.setCommand(0x20);
                Map<String, Object> dataMap=new HashMap<String, Object>();
                dataMap.put("CallId", callData.getCallId());
                dataMap.put("CallerId", callData.getCallerId());
                dataMap.put("CallederId", callData.getCallederId());
                dataMap.put("ACKType", "1");//可以通话
                MapContent mc=new MapContent(dataMap);
                toCallerMsg.setMsgContent(mc);
                toCallerMsg.setPCDType(0);
                globalMem.sendMem.putDeviceMsg(callData.getCallerKey(), toCallerMsg);
                callData.addSendedMsg(toCallerMsg);
            } else if (callData.getCallederList()!=null) {
                //告诉其他被叫设备
                for (PushUserUDKey _pUdkey: callData.getCallederList()) {
                    if (!callederKey.equals(_pUdkey)) {
                        MsgNormal toOtherCallederMsg=new MsgNormal();
                        toOtherCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                        toOtherCallederMsg.setFromType(1);
                        toOtherCallederMsg.setToType(0);
                        toOtherCallederMsg.setMsgType(0);
                        toOtherCallederMsg.setAffirm(0);
                        toOtherCallederMsg.setBizType(2);
                        toOtherCallederMsg.setCmdType(1);
                        toOtherCallederMsg.setCommand(0x20);
                        Map<String, Object> _dataMap=new HashMap<String, Object>();
                        _dataMap.put("CallId", callData.getCallId());
                        _dataMap.put("CallerId", callData.getCallerId());
                        _dataMap.put("CallederId", callData.getCallederId());
                        _dataMap.put("ACKType", "1");
                        MapContent _mc=new MapContent(_dataMap);
                        toOtherCallederMsg.setMsgContent(_mc);
                        toOtherCallederMsg.setPCDType(0);
                        globalMem.sendMem.putDeviceMsg(_pUdkey, toOtherCallederMsg);
                        callData.addSendedMsg(toOtherCallederMsg);
                    }
                }
            }
            PushUserUDKey callerK=callData.getUdkByUserId(callData.getCallerId());
            callerK=(PushUserUDKey)sessionService.getActivedUserUDK(callerK.getUserId(), callerK.getPCDType());
            //记录到已发送列表
            //修改状态
            callData.setStatus_3();
        }
    }

    //关闭
    private void shutdown() {
        if (callData.getStatus()<9) {
            synchronized(shutdownLock) {
                callData.setStatus_9();
                callingMem.removeUserInCall(callData.getCallerId(), callData);
                callingMem.removeUserInCall(callData.getCallederId(), callData);
                callingMem.removeOneCall(callData.getCallId());
                callData.clear();
                MsgNormal dualMsg=new MsgNormal();
                dualMsg.setMsgId("-1");
                try {
                    callData.putPreMsg(dualMsg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                moniterTimer.cancel();
                moniterTimer=null;
            }
        }
    }

    class MonitorOneCall extends TimerTask {
        @Override
        public void run() {
            //结束进程 或 已经挂断了
            if (CallHandler.this.callData.getStatus()==9||CallHandler.this.callData.getStatus()==4) shutdown();

            //一段时间后未收到自动回复，的处理
            if (CallHandler.this.callData.getStatus()==1
              &&CallHandler.this.callData.getBeginDialTime()!=-1
              &&(System.currentTimeMillis()-CallHandler.this.callData.getBeginDialTime()>CallHandler.this.conf.get_ExpireOnline()))
            {
                try {
                    dealOutLine();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                shutdown();
            }
            //一段时间后未收到“被叫者”手工应答Ack，的处理
            if ((CallHandler.this.callData.getStatus()==1||CallHandler.this.callData.getStatus()==2)
              &&CallHandler.this.callData.getBeginDialTime()!=-1
              &&(System.currentTimeMillis()-CallHandler.this.callData.getBeginDialTime()>CallHandler.this.conf.get_ExpireAck()))
            {
                try {
                    dealNoAck();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                shutdown();
            }
            //一段时间后未收到任何消息，通话过期
            if ((CallHandler.this.callData.getStatus()==1||CallHandler.this.callData.getStatus()==2||CallHandler.this.callData.getStatus()==3)
              &&(System.currentTimeMillis()-CallHandler.this.callData.getLastUsedTime()>CallHandler.this.conf.get_ExpireTime()))
            {
                try {
                    dealCallExpire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                shutdown();
            }
            //一段时间后未通话，删除通话者
            if (CallHandler.this.callData.getStatus()==3
              &&CallHandler.this.callData.getSpeaker()!=null
              &&System.currentTimeMillis()-CallHandler.this.callData.getLastTalkTime()>CallHandler.this.conf.get_ExpireSpeakerTime())
            {
                CallHandler.this.callData.clearSpeaker();
            }
            //“呼叫者”第一次说话
            if (!CallHandler.this.isCallederTalked&&callData.getCallederWts().size()==1) {
                try {
                    dealCallerFirstTalk(CallHandler.this.callData.getCallederWts().get(0).getTalkerUdk());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                CallHandler.this.isCallederTalked=true;
            }
        }
    }
}