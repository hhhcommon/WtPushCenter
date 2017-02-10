package com.woting.audioSNS.intercom.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.IntercomConfig;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
//import com.woting.audioSNS.mediaflow.mem.TalkMemory;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.ProcessedMsg;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;
import com.woting.push.core.message.content.MapContent;

public class IntercomHandler extends AbstractLoopMoniter<IntercomConfig> {
    private Logger logger=LoggerFactory.getLogger(IntercomHandler.class);

    private SessionService sessionService=null;
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private IntercomMemory interMem=IntercomMemory.getInstance();

    private OneMeet meetData=null;
    private volatile Object shutdownLock=new Object();
    private Timer moniterTimer=null;

    protected IntercomHandler(IntercomConfig conf, OneMeet om) {
        super(conf);
        super.setName("组对讲["+om.getGroupId()+"]消息处理线程");
        meetData=om;
        meetData.setIntercomHandler(this);
        //启动监控线程
        moniterTimer=new Timer("组对讲["+om.getGroupId()+"]监控线程", true);
        moniterTimer.scheduleAtFixedRate(new MonitorOneMeet(), 0, conf.get_ExpireSpeakerTime());
    }

    @Override
    public boolean initServer() {
        sessionService=(SessionService)SpringShell.getBean("sessionService");
        return sessionService!=null;
    }

    @Override
    public void oneProcess() throws Exception {
        try {
            MsgNormal m=meetData.takePreMsg();
            if (m==null||m.getMsgId().equals("-1")) return;

            meetData.setLastUsedTime();
            ProcessedMsg pMsg=new ProcessedMsg(m, System.currentTimeMillis(), getClass().getName());
            int flag=1;

            try {
                String tempStr;
                if (m.getCmdType()==1) {//组控制信息
                    if (m.getCommand()==1) {
                        tempStr="处理消息[MsgId="+m.getMsgId()+"]-用户进入组::(User="+PushUserUDKey.buildFromMsg(m)+";Group="+((MapContent)m.getMsgContent()).get("GroupId")+")";
                        logger.debug(tempStr);
                        flag=enterGroup(m);
                    } else if (m.getCommand()==2) {
                        tempStr="处理消息[MsgId="+m.getMsgId()+"]-用户退出组::(User="+PushUserUDKey.buildFromMsg(m)+";Group="+((MapContent)m.getMsgContent()).get("GroupId")+")";
                        logger.debug(tempStr);
                        flag=exitGroup(m);
                    }
                } else if (m.getCmdType()==2) {//对讲控制信息
                    if (m.getCommand()==1) {
                        tempStr="处理消息[MsgId="+m.getMsgId()+"]-开始对讲::(User="+PushUserUDKey.buildFromMsg(m)+";Group="+((MapContent)m.getMsgContent()).get("GroupId")+")";
                        logger.debug(tempStr);
                        flag=beginPTT(m);
                    } else if (m.getCommand()==2) {
                        tempStr="处理消息[MsgId="+m.getMsgId()+"]-结束对讲::(User="+PushUserUDKey.buildFromMsg(m)+";Group="+((MapContent)m.getMsgContent()).get("GroupId")+")";
                        logger.debug(tempStr);
                        flag=endPTT(m);
                    }
                }
                pMsg.setStatus(flag);
            } catch(Exception e) {
                logger.debug(StringUtils.getAllMessage(e));
                pMsg.setStatus(-1);
            } finally {
                pMsg.setEndTime(System.currentTimeMillis());
                meetData.addProcessedMsg(pMsg);
            }
        } catch(Exception e) {
            logger.debug(StringUtils.getAllMessage(e));
        }
    }

    @Override
    public boolean canContinue() {
        return meetData.getStatus()!=9;
    }

    //进入组处理
    private int enterGroup(MsgNormal m) throws InterruptedException {
        if (m==null) return 2;
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return 2;

        String groupId="";
        try {
            groupId+=((MapContent)m.getMsgContent()).get("GroupId");
        } catch(Exception e) {}
        if (groupId.length()==0) return 2;

        MsgNormal retMsg=MessageUtils.buildRetMsg(m);
        retMsg.setCommand(9);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("GroupId", groupId);
        MapContent mc=new MapContent(dataMap);
        retMsg.setMsgContent(mc);

        int retFlag=0;
        if (!pUdk.isUser()) retMsg.setReturnType(0x00);
        else if (meetData==null) retMsg.setReturnType(0x02);
        else {
            retFlag=meetData.insertEntryUser(pUdk);
            if (retFlag==4||retFlag==5) retMsg.setReturnType(0x04);//该用户不在指定组
            else if (retFlag==2) retMsg.setReturnType(0x08);//该用户已进入指定组
            else retMsg.setReturnType(0x01);//正确加入组
        }
        globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
        //判断组内是否有人说话，若有向进组人发送说话者消息
        if (meetData.getSpeaker()!=null) {
            MsgNormal hasSpeaker=MessageUtils.clone(retMsg);
            hasSpeaker.setReMsgId(null);
            hasSpeaker.setMsgType(0);
            hasSpeaker.setCmdType(2);
            hasSpeaker.setCommand(0x30);
            dataMap=new HashMap<String, Object>();
            dataMap.put("GroupId", groupId);
            dataMap.put("SpeakerId", meetData.getSpeaker().getUserId());
            MapContent _mc=new MapContent(dataMap);
            hasSpeaker.setMsgContent(_mc);
            globalMem.sendMem.putDeviceMsg(pUdk, hasSpeaker);
        }

        //进组成功广播消息信息组织
        if (retFlag==1&&meetData.getEntryGroupUserMap()!=null&&meetData.getEntryGroupUserMap().size()>1) {
            //组织消息
            MsgNormal bMsg=MessageUtils.clone(retMsg);
            bMsg.setReMsgId(null);
            bMsg.setMsgType(0);
            bMsg.setCommand(0x10);
            dataMap=new HashMap<String, Object>();
            dataMap.put("GroupId", groupId);
            List<Map<String, Object>> inGroupUsers=new ArrayList<Map<String,Object>>();
            for (String k: meetData.getEntryGroupUserMap().keySet()) {
                Map<String, Object> um;
                UserPo up=meetData.getEntryGroupUserMap().get(k);
                um=new HashMap<String, Object>();
                um.put("UserId", up.getUserId());
                inGroupUsers.add(um);
            }
            dataMap.put("InGroupUsers", inGroupUsers);
            MapContent _mc=new MapContent(dataMap);
            bMsg.setMsgContent(_mc);
            //发送广播消息
            for (String k: meetData.getEntryGroupUserMap().keySet()) {
                List<PushUserUDKey> al=sessionService.getActivedUserUDKs(k);
                if (al!=null&&!al.isEmpty()) {
                    for (PushUserUDKey _pUdk: al) {
                        globalMem.sendMem.putDeviceMsg(_pUdk, bMsg);
                    }
                }
            }
//            //给自己的其他设备也发这样的消息
//            List<PushUserUDKey> al=sessionService.getActivedUserUDKs(pUdk.getUserId());
//            if (al!=null&&!al.isEmpty()) {
//                for (PushUserUDKey _pUdk: al) {
//                    if (_pUdk.equals(pUdk)) continue;
//                    globalMem.sendMem.addUnionUserMsg(_pUdk, retMsg, new CompareGroupMsg());
//                }
//            }
        }
        return retFlag==1?1:3;
    }

    //退出组处理
    private int exitGroup(MsgNormal m) throws InterruptedException {
        if (m==null) return 2;
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return 2;

        String groupId="";
        try {
            groupId+=((MapContent)m.getMsgContent()).get("GroupId");
        } catch(Exception e) {}
        if (groupId.length()==0) return 2;

        MsgNormal retMsg=MessageUtils.buildRetMsg(m);
        retMsg.setCommand(0x0A);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("GroupId", groupId);
        MapContent mc=new MapContent(dataMap);
        retMsg.setMsgContent(mc);

        int retFlag=0;
        if (!pUdk.isUser()) retMsg.setReturnType(0x00);
        else if (meetData==null) retMsg.setReturnType(0x02);
        else {
            retFlag=meetData.deleteEntryUser(pUdk);
            if (retFlag==4||retFlag==5) retMsg.setReturnType(0x04);//该用户不在指定组
            else if (retFlag==3) retMsg.setReturnType(0x08);//该用户未进入指定组
            else retMsg.setReturnType(0x01);//正确离开组
        }
        globalMem.sendMem.putDeviceMsg(pUdk, retMsg);

        //广播消息信息组织
        if (retFlag==1&&meetData.getEntryGroupUserMap()!=null) {
            if (!meetData.getEntryGroupUserMap().isEmpty()) {
                MsgNormal bMsg=MessageUtils.clone(retMsg);
                bMsg.setReMsgId(null);
                bMsg.setMsgType(0);
                bMsg.setCommand(0x10);
                dataMap=new HashMap<String, Object>();
                dataMap.put("GroupId", groupId);
                List<Map<String, Object>> inGroupUsers=new ArrayList<Map<String,Object>>();
                for (String k: meetData.getEntryGroupUserMap().keySet()) {
                    Map<String, Object> um;
                    UserPo up=meetData.getEntryGroupUserMap().get(k);
                    um=new HashMap<String, Object>();
                    um.put("UserId", up.getUserId());
                    inGroupUsers.add(um);
                }
                dataMap.put("InGroupUsers", inGroupUsers);
                MapContent _mc=new MapContent(dataMap);
                bMsg.setMsgContent(_mc);
                //发送广播消息
                for (String k: meetData.getEntryGroupUserMap().keySet()) {
                    List<PushUserUDKey> al=sessionService.getActivedUserUDKs(k);
                    if (al!=null&&!al.isEmpty()) {
                        for (PushUserUDKey _pUdk: al) {
                            globalMem.sendMem.putDeviceMsg(_pUdk, bMsg);
                        }
                    }
                }
//                //给自己的其他设备也发这样的消息
//                List<PushUserUDKey> al=sessionService.getActivedUserUDKs(pUdk.getUserId());
//                if (al!=null&&!al.isEmpty()) {
//                    for (PushUserUDKey _pUdk: al) {
//                        if (!_pUdk.equals(pUdk)) {
//                            globalMem.sendMem.addUnionUserMsg(_pUdk, retMsg, new CompareGroupMsg());
//                        }
//                    }
//                }
            }
        }
        return retFlag==1?1:3;
    }

    private int beginPTT(MsgNormal m) throws InterruptedException {
        if (m==null) return 2;
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return 2;

        String groupId="";
        try {
            groupId+=((MapContent)m.getMsgContent()).get("GroupId");
        } catch(Exception e) {}
        if (groupId.length()==0) return 2;

        MsgNormal retMsg=MessageUtils.buildRetMsg(m);
        retMsg.setCommand(9);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("GroupId", groupId);
        MapContent mc=new MapContent(dataMap);
        retMsg.setMsgContent(mc);

        Map<String, Object> retMap=null;
        int retFlag=-1;
        if (!pUdk.isUser()) retMsg.setReturnType(0x00);
        else if (meetData==null) retMsg.setReturnType(0x02);
        else {
            retMap=meetData.setSpeaker(pUdk);
            retFlag=Integer.parseInt(""+retMap.get("retFlag"));
            if (retFlag==4) retMsg.setReturnType(0x04);//该用户不在指定组
            else if (retFlag==2||retFlag==3) retMsg.setReturnType(0x05);//用户组在线人数不足
            else if (retFlag==5) {//已经有人通话
                retMsg.setReturnType(0x08);
                dataMap.put("SpeakerId", retMap.get("speakerId"));
            }
            else if (retFlag==6) retMsg.setReturnType(0x06);//自己在用其他设备通话
            else if (retFlag==7) retMsg.setReturnType(0x07);//自己在电话通话
            else retMsg.setReturnType(0x01);//正确加入组
        }
        globalMem.sendMem.putDeviceMsg(pUdk, retMsg);

        if (retFlag==1&&meetData.getEntryGroupUserMap()!=null&&meetData.getEntryGroupUserMap().size()>1) {
            MsgNormal bMsg=MessageUtils.clone(retMsg);
            bMsg.setReMsgId(null);
            bMsg.setMsgType(0);
            bMsg.setCommand(0x10);
            dataMap=new HashMap<String, Object>();
            dataMap.put("GroupId", groupId);
            dataMap.put("SpeakerId", pUdk.getUserId());
            MapContent _mc=new MapContent(dataMap);
            bMsg.setMsgContent(_mc);
            //发送广播消息
            for (String k: meetData.getEntryGroupUserMap().keySet()) {
                List<PushUserUDKey> al=sessionService.getActivedUserUDKs(k);
                if (al!=null&&!al.isEmpty()) {
                    for (PushUserUDKey _pUdk: al) {
                        if (_pUdk.equals(pUdk)) continue;
                        globalMem.sendMem.putDeviceMsg(_pUdk, bMsg);
                    }
                }
            }
        }
        return retFlag==1?1:3;
    }

    private int endPTT(MsgNormal m) throws InterruptedException {
        if (m==null) return 2;
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(m);
        if (pUdk==null) return 2;

        String groupId="";
        try {
            groupId+=((MapContent)m.getMsgContent()).get("GroupId");
        } catch(Exception e) {}
        if (groupId.length()==0) return 2;

        MsgNormal retMsg=MessageUtils.buildRetMsg(m);
        retMsg.setCommand(0x0A);
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("GroupId", groupId);
        MapContent mc=new MapContent(dataMap);
        retMsg.setMsgContent(mc);

        int retFlag=0;
        if (!pUdk.isUser()) retMsg.setReturnType(0x00);
        else if (meetData==null) retMsg.setReturnType(0x02);
        else {
//            System.out.println("^^005^^^"+meetData.getGroupId()+"^^^^^^^^^^^^^^^^^^^^^^^ in Handler endPTT");
            retFlag=meetData.releaseSpeaker(pUdk);
            if (retFlag==2) retMsg.setReturnType(0x04);//该用户不在指定组
            else if (retFlag==3) {
                retMsg.setReturnType(0x08);//该用户和当前对讲用户不匹配
                dataMap.put("SpeakerId", meetData.getSpeaker().getUserId());
            }
            else retMsg.setReturnType(0x01);//正确结束PTT
            //删除语音内容
            //globalMem.sendMem.cleanMsg4InterComSpeak(meetData.getGroupId(), ); //清除说话者未发出的语音信息
        }
        globalMem.sendMem.putDeviceMsg(pUdk, retMsg);

        if (retFlag==1) {
            MsgNormal bMsg=MessageUtils.clone(retMsg);
            bMsg.setReMsgId(null);
            bMsg.setMsgType(0);
            bMsg.setCommand(0x20);
            dataMap=new HashMap<String, Object>();
            dataMap.put("GroupId", groupId);
            dataMap.put("SpeakerId", pUdk.getUserId());
            MapContent _mc=new MapContent(dataMap);
            bMsg.setMsgContent(_mc);
            //发送广播消息
            for (String k: meetData.getEntryGroupUserMap().keySet()) {
                List<PushUserUDKey> al=sessionService.getActivedUserUDKs(k);
                if (al!=null&&!al.isEmpty()) {
                    for (PushUserUDKey _pUdk: al) {
                        if (_pUdk.equals(pUdk)) continue;
                        globalMem.sendMem.putDeviceMsg(_pUdk, bMsg);
                    }
                }
            }
        }
        return retFlag==1?1:3;
    }

    //=====以下为清除和关闭的操作
    //关闭
    private void shutdown() {
        if (meetData.getStatus()<9) {
            synchronized(shutdownLock) {
                meetData.setStatus_9();
                interMem.removeOneMeet(meetData.getGroupId());
                meetData.clear();
                MsgNormal dualMsg=new MsgNormal();
                dualMsg.setMsgId("-1");
                try {
                    meetData.putPreMsg(dualMsg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                globalMem.sendMem.cleanMsg4Intercom(meetData); //清除本对讲所涉及的未发出的消息
                moniterTimer.cancel();
                moniterTimer=null;
            }
        }
    }

    class MonitorOneMeet extends TimerTask {
        @Override
        public void run() {
            //首先判断，是否可以继续通话
            int _s=IntercomHandler.this.meetData.getStatus();
            if (_s==9||_s==4) {
                shutdown();//结束进程
                return;
            }
            //一段时间后未收到任何消息，通话过期
            if ((IntercomHandler.this.meetData.getStatus()==1||IntercomHandler.this.meetData.getStatus()==2||IntercomHandler.this.meetData.getStatus()==3)
              &&(System.currentTimeMillis()-IntercomHandler.this.meetData.getLastUsedTime()>IntercomHandler.this.conf.get_ExpireTime()))
            {
                shutdown();
                return;
            }
            //一段时间后未通话，删除通话者
            if ((IntercomHandler.this.meetData.getStatus()==2||IntercomHandler.this.meetData.getStatus()==3)
              &&(IntercomHandler.this.meetData.getSpeaker()!=null)
              &&(System.currentTimeMillis()-IntercomHandler.this.meetData.getLastTalkTime()>IntercomHandler.this.conf.get_ExpireSpeakerTime()))
            {
//                System.out.println("^^002^^^"+meetData.getGroupId()+"^^^^^^^^^^^^^^^^^^^^^^^"+System.currentTimeMillis()+"-"+IntercomHandler.this.meetData.getLastTalkTime()+"="+(System.currentTimeMillis()-IntercomHandler.this.meetData.getLastTalkTime())+">"+IntercomHandler.this.conf.get_ExpireSpeakerTime());
                IntercomHandler.this.meetData.clearSpeaker();
            }
        }
    }
}