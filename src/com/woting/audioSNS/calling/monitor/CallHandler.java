package com.woting.audioSNS.calling.monitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.push.core.mem.TcpGlobalMemory;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.ProcessedMsg;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.passport.UGA.service.UserService;
import com.woting.push.user.PushUserUDKey;

/**
 * 电话控制线程
 * @author wanghui
 */
public class CallHandler extends AbstractLoopMoniter<CallingConfig> {
    private Logger logger=LoggerFactory.getLogger(CallHandler.class);

    private TcpGlobalMemory globalMem=TcpGlobalMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();

    private OneCall callData;//所控制的通话数据
    private boolean isCallerTalked=false; //是否“被叫者”说话

    private PushUserUDKey callerKey=null;
    private PushUserUDKey callederKey=null;

    private SessionService sessionService=null;
    private UserService userService=null;

    /**
     * 构造函数，必须给定一个通话控制数据
     * @param callData
     */
    protected CallHandler(CallingConfig conf, OneCall oneCall, SessionService sessionService, UserService userService) {
        super(conf);
        super.setName("电话处理["+oneCall.getCallId()+"]监控主线程");
        this.setLoopDelay(10);
        this.callData=oneCall;
        this.sessionService=sessionService;
        this.userService=userService;
        this.callData.setCallHandler(this);
        this.callData.setBeginDialTime();
    }

    @Override
    public boolean canContinue() {
        return callData.getStatus()!=9;
    }

    @Override
    public void oneProcess() throws Exception {
        try {
            sleep(50);//等50毫秒
            if (callData.getStatus()==9) {//结束进程
                shutdown();
                return;
            }
            if (callData.getStatus()==4) {//已经挂断了 //清除声音内容
                cleanTalk(1);
            }

            //一段时间后未收到自动回复，的处理
            if ((this.callData.getStatus()==1||this.callData.getStatus()==0)
              &&this.callData.getBeginDialTime()!=-1
              &&(System.currentTimeMillis()-this.callData.getBeginDialTime()>this.callData.getExpireOnline()))
            {
                dealOutLine();
            }
            //一段时间后未收到“被叫者”手工应答Ack，的处理
            if ((this.callData.getStatus()==0||this.callData.getStatus()==1||this.callData.getStatus()==2)
              &&this.callData.getBeginDialTime()!=-1
              &&(System.currentTimeMillis()-this.callData.getBeginDialTime()>this.callData.getExpireAck()))
            {
                dealNoAck();
            }
            //一段时间后未收到任何消息，通话过期
            if ((this.callData.getStatus()==0||this.callData.getStatus()==1||this.callData.getStatus()==2||this.callData.getStatus()==3)
              &&(System.currentTimeMillis()-this.callData.getLastUsedTime()>this.callData.getExpireTime()))
            {
                dealCallExpire();
            }

            //“被叫者”第一次说话
            if (!isCallerTalked&&this.callData.getCallederWts().size()>0) {
                dealCallerFirstTalk();
                isCallerTalked=true;
            }
            //读取预处理的消息
            MsgNormal m=this.callData.pollPreMsg();//第一条必然是呼叫信息
            if (m==null) return;

            this.callData.setLastUsedTime();
            int flag=1;
            ProcessedMsg pMsg=new ProcessedMsg(m, System.currentTimeMillis(), this.getClass().getName());
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
            } catch(Exception e) {
                pMsg.setStatus(-1);
            } finally {
                pMsg.setEndTime(System.currentTimeMillis());
                this.callData.addProcessedMsg(pMsg);
            }
        } catch(Exception e) {
            e.printStackTrace();
            cleanTalk(2);//强制清除声音
        }
    }

    //===========以下是分步处理过程，全部是私有函数
    //处理呼叫(CALL:1)
    private void dial(MsgNormal m) {
        System.out.println("处理呼叫信息前==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
        PushUserUDKey callerUdk=PushUserUDKey.buildFromMsg(m);
        String callId =((MapContent)m.getMsgContent()).get("CallId")+"";
        String callerId=PushUserUDKey.buildFromMsg(m).getUserId();
        String callederId=((MapContent)m.getMsgContent()).get("CallederId")+"";

        Map<String, Object> dataMap=null;

        //返回给呼叫者的消息
        MsgNormal toCallerMsg=new MsgNormal();
        toCallerMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setReMsgId(m.getMsgId());
        toCallerMsg.setFromType(m.getToType());
        toCallerMsg.setToType(m.getFromType());
        toCallerMsg.setMsgType(1);
        toCallerMsg.setBizType(0x02);
        toCallerMsg.setCmdType(1);
        toCallerMsg.setCommand(0x09);
        //判断呼叫者是否存在
        boolean callerExisted=true; //是否呼叫者或被叫者存在
        if ((PushUserUDKey)sessionService.getActivedUserUDK(callerUdk.getUserId(),callerUdk.getPCDType())==null) {
            toCallerMsg.setReturnType(2);
            callerExisted=false;
        }
        List<PushUserUDKey> calleredDevices=null;
        if (callerExisted) {
            calleredDevices=(List<PushUserUDKey>)sessionService.getActivedUserUDKs(callederId);
            if (calleredDevices==null||calleredDevices.isEmpty()) {
                toCallerMsg.setReturnType(3);
                callerExisted=false;
            }
        }
        //判断是否占线
        boolean isBusy=true; //是否占线
        if (callerExisted) {
            if (callerId.equals(callederId))  toCallerMsg.setReturnType(6);
            else
//            if (gmm.isTalk(callederId)) toCallerMsg.setReturnType(5);
//            else
            if (callingMem.isTalk(callederId, callId)) toCallerMsg.setReturnType(4);
            else isBusy=false;
        }
        if (callerExisted&&!isBusy) toCallerMsg.setReturnType(1);
        dataMap=new HashMap<String, Object>();
        dataMap.put("CallId", callId);
        dataMap.put("CallerId", callerId);
        dataMap.put("CallederId", callederId);
        MapContent mc=new MapContent(dataMap);
        toCallerMsg.setMsgContent(mc);
        globalMem.sendMem.addUserMsg(callerUdk, toCallerMsg);
        //记录到已发送列表
        this.callData.addSendedMsg(toCallerMsg);

        //给被叫者发送信息
        if (callerExisted&&!callerId.equals(callederId)) {
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
            dataMap.put("DialType", isBusy?"2":"1");
            dataMap.put("CallId", callId);
            dataMap.put("CallerId", callerId);
            dataMap.put("CallederId", callederId);
            MapContent _mc=new MapContent(dataMap);
            toCallederMsg.setMsgContent(_mc);
            //加入“呼叫者”的用户信息给被叫者
            Map<String, Object> callerInfo=new HashMap<String, Object>();
            UserPo u=userService.getUserById(callerId);
            callerInfo.put("UserName", u.getLoginName());
            callerInfo.put("UserNum", u.getUserNum());
            callerInfo.put("Portrait", u.getPortraitMini());
            callerInfo.put("Mail", u.getMailAddress());
            callerInfo.put("Descn", u.getDescn());
            dataMap.put("CallerInfo", callerInfo);

            for (PushUserUDKey pUdk: calleredDevices) {
                globalMem.sendMem.addUserMsg(pUdk, toCallederMsg);
            }
            //记录到已发送列表
            this.callData.addSendedMsg(toCallederMsg);
        }

        //若不存在用户或占线要删除数据及这个过程
        if (!callerExisted||isBusy) shutdown();
        else {
            //修改状态
            this.callData.setStatus_1();
            this.callerKey=callerUdk;
            this.callData.setCallerKey(callerUdk);
            this.callederKey=null;
        }
        logger.debug("处理呼叫信息后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
    }

    //处理“被叫者”的自动呼叫反馈(CALL:-b1)
    private int dealAutoDialFeedback(MsgNormal m) {
        System.out.println("处理自动应答前==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
        //首先判断这个消息是否符合处理的要求：callid, callerId, callederId是否匹配
        if (!this.callData.getCallerId().equals(((MapContent)m.getMsgContent()).get("CallerId")+"")||!this.callData.getCallederId().equals(PushUserUDKey.buildFromMsg(m).getUserId())) return 3;
        if (this.callData.getStatus()==1) {//状态正确，如果是其他状态，这个消息抛弃
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
            dataMap.put("CallId", this.callData.getCallId());
            dataMap.put("CallerId", this.callData.getCallerId());
            dataMap.put("CallederId", this.callData.getCallederId());
            dataMap.put("OnLineType", "1");
            MapContent mc=new MapContent(dataMap);
            toCallerMsg.setMsgContent(mc);

            this.callederKey=PushUserUDKey.buildFromMsg(m); //被叫者Key
            this.callData.setCallederKey(callederKey);
            
            globalMem.sendMem.addUserMsg(this.callerKey, toCallerMsg);
            this.callData.addSendedMsg(toCallerMsg);//记录到已发送列表

            this.callData.setStatus_2();//修改状态
            System.out.println("处理自动应答后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
            return 1;
        } else return 2;//被抛弃
    }

    //处理“被叫者”应答(CALL:2)
    private int ackDial(MsgNormal m) {
        //首先判断这个消息是否符合处理的要求：callid, callerId, callederId是否匹配
        if (!this.callData.getCallerId().equals(((MapContent)m.getMsgContent()).get("CallerId")+"")||!this.callData.getCallederId().equals(PushUserUDKey.buildFromMsg(m).getUserId())) return 3;
        if (this.callData.getStatus()==1||this.callData.getStatus()==2) {//状态正确，如果是其他状态，这个消息抛弃
            //应答状态
            int ackType=2; //拒绝
            try {
                ackType=Integer.parseInt(""+((MapContent)m.getMsgContent()).get("ACKType"));
            } catch(Exception e) {}

            PushUserUDKey _tempK=PushUserUDKey.buildFromMsg(m);
            if (this.callederKey==null) {
                this.callederKey=_tempK;
                this.callData.setCallederPcdType(_tempK.getPCDType());
            } else return 2;//已处理了，不再处理了。

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
            dataMap.put("CallId", this.callData.getCallId());
            dataMap.put("CallerId", this.callData.getCallerId());
            dataMap.put("CallederId", this.callData.getCallederId());
            dataMap.put("ACKType", ackType);
            MapContent mc=new MapContent(dataMap);
            toCallerMsg.setMsgContent(mc);

            globalMem.sendMem.addUserMsg(this.callerKey, toCallerMsg);
            this.callData.addSendedMsg(toCallerMsg);//记录到已发送列表

            if (ackType==1) this.callData.setStatus_3();//修改状态:正常通话
            else
            if (ackType==2||ackType==31) this.callData.setStatus_4();//修改状态:挂断
            System.out.println("接到被叫者手工应答后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
            return 1;
        } else return 2;//被抛弃
    }

    //处理“挂断”(CALL:3)
    private int hangup(MsgNormal m) {
        //首先判断是那方在进行挂断
        String hangupperId=PushUserUDKey.buildFromMsg(m).getUserId();
        String otherId=this.callData.getOtherId(hangupperId);
        if (otherId==null) return 3;
        PushUserUDKey otherK=this.callData.getOtherUdk(hangupperId);
        if (otherK.getPCDType()!=1&&otherK.getPCDType()!=2) return 3;

        //给另一方发送“挂断传递”消息
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
        dataMap.put("CallId", this.callData.getCallId());
        dataMap.put("CallerId", this.callData.getCallerId());
        dataMap.put("CallederId", this.callData.getCallederId());
        dataMap.put("HangupType", "1");
        MapContent mc=new MapContent(dataMap);
        otherMsg.setMsgContent(mc);

        globalMem.sendMem.addUserMsg(otherK, otherMsg);
        this.callData.addSendedMsg(otherMsg);//记录到已发送列表
        
        this.callData.setStatus_4();//修改状态
        System.out.println("处理挂断消息后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
        return 1;
    }

    //处理开始对讲(PTT:1)
    private void beginPTT(MsgNormal m) {
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
        String speaker=PushUserUDKey.buildFromMsg(m).getUserId();
        PushUserUDKey speakerK=this.callData.getUdkByUserId(speaker);
        speakerK=(PushUserUDKey)sessionService.getActivedUserUDK(speakerK.getUserId(), speakerK.getPCDType());

        if (StringUtils.isNullOrEmptyOrSpace(speaker)||speakerK==null) {
            toSpeakerMsg.setReturnType(0);
        } else {
            String ret=this.callData.setSpeaker(speaker);
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
        dataMap.put("CallId", this.callData.getCallId());
        MapContent mc=new MapContent(dataMap);
        toSpeakerMsg.setMsgContent(mc);
        globalMem.sendMem.addUserMsg(PushUserUDKey.buildFromMsg(m), toSpeakerMsg);
        //记录到已发送列表
        this.callData.addSendedMsg(toSpeakerMsg);
    }

    //处理结束对讲(PTT:2)
    private void endPTT(MsgNormal m) {
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
        String speaker=PushUserUDKey.buildFromMsg(m).getUserId();
        PushUserUDKey speakerK=this.callData.getUdkByUserId(speaker);
        speakerK=(PushUserUDKey)sessionService.getActivedUserUDK(speakerK.getUserId(), speakerK.getPCDType());

        if (speakerK==null) {
            toSpeakerMsg.setReturnType(0);
        } else {
            String ret=this.callData.cleanSpeaker(speaker);
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
        dataMap.put("CallId", this.callData.getCallId());
        MapContent mc=new MapContent(dataMap);
        toSpeakerMsg.setMsgContent(mc);
        globalMem.sendMem.addUserMsg(PushUserUDKey.buildFromMsg(m), toSpeakerMsg);
        //记录到已发送列表
        this.callData.addSendedMsg(toSpeakerMsg);
    }

    //=======以下3个超时处理
    //处理“被叫者”不在线
    private void dealOutLine() {
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
        dataMap.put("CallId", this.callData.getCallId());
        dataMap.put("CallerId", this.callData.getCallerId());
        dataMap.put("CallederId", this.callData.getCallederId());
        dataMap.put("OnLineType", "2");
        MapContent mc=new MapContent(dataMap);
        toCallerMsg.setMsgContent(mc);

        globalMem.sendMem.addUserMsg(this.callerKey, toCallerMsg);
        this.callData.addSendedMsg(toCallerMsg);//记录到已发送列表

        shutdown();
        System.out.println("被叫者不在线检测到后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
    }

    //处理“被叫者”未手工应答
    private void dealNoAck() {
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
        dataMap.put("CallId", this.callData.getCallId());
        dataMap.put("CallerId", this.callData.getCallerId());
        dataMap.put("CallederId", this.callData.getCallederId());
        dataMap.put("ACKType", "32");
        MapContent mc=new MapContent(dataMap);
        toCallerMsg.setMsgContent(mc);
        globalMem.sendMem.addUserMsg(this.callerKey, toCallerMsg);
        this.callData.addSendedMsg(toCallerMsg);//记录到已发送列表

        //2、构造“挂断传递”消息，并发送给“被叫者”
        MsgNormal toCallederMsg=new MsgNormal();
        toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setFromType(1);
        toCallerMsg.setToType(0);
        toCallederMsg.setMsgType(0);
        toCallederMsg.setAffirm(1);
        toCallederMsg.setBizType(2);
        toCallederMsg.setCmdType(1);
        toCallederMsg.setCommand(0x30);
        dataMap=new HashMap<String, Object>();
        dataMap.put("CallId", this.callData.getCallId());
        dataMap.put("CallerId", this.callData.getCallerId());
        dataMap.put("CallederId", this.callData.getCallederId());
        dataMap.put("HangupType", "2");
        MapContent _mc=new MapContent(dataMap);
        toCallederMsg.setMsgContent(_mc);
        globalMem.sendMem.addUserMsg(this.callederKey, toCallederMsg);
        this.callData.addSendedMsg(toCallederMsg);//记录到已发送列表

        shutdown();
        System.out.println("未手工应答后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
    }

    //服务器发现电话过程过期
    private void dealCallExpire() {
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
        callerMap.put("CallId", this.callData.getCallId());
        callerMap.put("CallerId", this.callData.getCallerId());
        callerMap.put("CallederId", this.callData.getCallederId());
        callerMap.put("HangupType", "3");
        MapContent mc=new MapContent(callerMap);
        toCallerMsg.setMsgContent(mc);
        globalMem.sendMem.addUserMsg(this.callerKey, toCallerMsg);
        this.callData.addSendedMsg(toCallerMsg);//记录到已发送列表

        //发送给“被叫者”的消息
        MsgNormal toCallederMsg=new MsgNormal();
        toCallederMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        toCallerMsg.setFromType(1);
        toCallerMsg.setToType(0);
        toCallederMsg.setMsgType(0);
        toCallederMsg.setAffirm(1);
        toCallerMsg.setBizType(2);
        toCallederMsg.setCmdType(1);
        toCallederMsg.setCommand(0x30);
        Map<String, Object> callederMap=new HashMap<String, Object>();
        callederMap.put("CallId", this.callData.getCallId());
        callederMap.put("CallerId", this.callData.getCallerId());
        callederMap.put("CallederId", this.callData.getCallederId());
        callederMap.put("HangupType", "3");
        MapContent _mc=new MapContent(callederMap);
        toCallederMsg.setMsgContent(_mc);
        globalMem.sendMem.addUserMsg(this.callederKey, toCallederMsg);
        this.callData.addSendedMsg(toCallederMsg);//记录到已发送列表

        shutdown();
        System.out.println("通话检测到超时==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
    }

    //===处理第一次被叫者说话的特殊流程
    private void  dealCallerFirstTalk() {
        if (this.callData.getStatus()==1||this.callData.getStatus()==2) {//等同于“被叫者”手工应答
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
            dataMap.put("CallId", this.callData.getCallId());
            dataMap.put("CallerId", this.callData.getCallerId());
            dataMap.put("CallederId", this.callData.getCallederId());
            dataMap.put("ACKType", "1");//可以通话
            MapContent mc=new MapContent(dataMap);
            toCallerMsg.setMsgContent(mc);

            PushUserUDKey callerK=this.callData.getUdkByUserId(this.callData.getCallerId());
            callerK=(PushUserUDKey)sessionService.getActivedUserUDK(callerK.getUserId(), callerK.getPCDType());

            globalMem.sendMem.addUserMsg(callerK, toCallerMsg);
            //记录到已发送列表
            this.callData.addSendedMsg(toCallerMsg);
            //修改状态
            this.callData.setStatus_3();
            System.out.println("第一次“被叫者”通话，并处理后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
        }
    }

    //=====以下三个为清除和关闭的操作
    //关闭
    private void shutdown() {
        cleanData();
        cleanTalk(2);
        callData.setStatus_9();
        logger.debug("结束进程后1==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
    }

    //清除数据，把本电话控制的数据从内存数据链中移除
    private void cleanData() {
        //把内容写入日志文件
        logger.debug("结束进行后2==清除数据前[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
        //清除未发送消息
        globalMem.sendMem.cleanMsg4Call(this.callerKey, this.callData.getCallId()); //清除呼叫者信息
        globalMem.sendMem.cleanMsg4Call(this.callederKey, this.callData.getCallId()); //清除被叫者信息
        callingMem.removeCallData(this.callData.getCallId());
        this.callData.getCallerWts().clear();
        this.callData.getCallederWts().clear();
        this.callData=null;
    }

    /* 
     * 清除语音数据
     * @param type 若=2是强制删除，不管是否语音传递完成
     */
    private void cleanTalk(int type) {
        //目前都是强制删除
        //删除talk内存数据链
     //   TalkMemoryManage tmm=TalkMemoryManage.getInstance();
     //   tmm.cleanCallData(this.callData.getCallId());
        logger.debug("清除语音数据后==[callid="+this.callData.getCallId()+"]:status="+this.callData.getStatus());
    }
}