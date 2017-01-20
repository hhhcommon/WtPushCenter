package com.woting.audioSNS.mediaflow.monitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysql.jdbc.StringUtils;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.audioSNS.mediaflow.mem.TalkMemory;
import com.woting.audioSNS.mediaflow.model.OneTalk;
import com.woting.audioSNS.mediaflow.model.TalkSegment;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.service.SessionService;
import com.woting.push.user.PushUserUDKey;

public class DealMediaMsg extends Thread {
    private Logger logger=LoggerFactory.getLogger(DealMediaMsg.class);

    private PushGlobalMemory globalMem=null;
    private IntercomMemory intercomMem=null;
    private CallingMemory callingMem=null;
    private TalkMemory talkMem=null;

    private SessionService sessionService=null;

    private MsgMedia sourceMsg=null;

    public DealMediaMsg(String name, MsgMedia sourceMsg, PushGlobalMemory globalMem, IntercomMemory intercomMem, CallingMemory callingMem, TalkMemory talkMem, SessionService sessionService) {
        setName(name);
        setDaemon(true);

        this.globalMem=globalMem;
        this.intercomMem=intercomMem;
        this.callingMem=callingMem;
        this.talkMem=talkMem;

        this.sessionService=sessionService;

        this.sourceMsg=sourceMsg;
    }

    public void run() {
        long beginTime=System.currentTimeMillis();
        if (sourceMsg.isAck()) dealAnswer();
        else {
            if (sourceMsg.getMediaType()==1) dealAudioDatagram();
        }
        logger.debug(this.getName()+"【用时{}】", System.currentTimeMillis()-beginTime);
    }

    //处理应答消息
    private void dealAnswer() {
        PushUserUDKey pUdk=(PushUserUDKey)sourceMsg.getExtInfo();
        if (pUdk==null||!pUdk.isUser()) return;
        String talkerId=pUdk.getUserId();
        String talkId=sourceMsg.getTalkId();
        if (StringUtils.isEmptyOrWhitespaceOnly(talkerId)) return;
        int seqNum=sourceMsg.getSeqNo();
        if (seqNum<0) return;
        String groupId=sourceMsg.getObjId();
        if (StringUtils.isEmptyOrWhitespaceOnly(groupId)) return;
        OneTalk wt=talkMem.getOneTalk(talkId);
        if (wt!=null) {
            if (sourceMsg.getReturnType()==1) {
                TalkSegment ts=wt.getTalkData().get(Math.abs(seqNum));
                if (ts!=null&&ts.getSendFlagMap().get(pUdk.toString())!=null) ts.getSendFlagMap().put(pUdk.toString(), 2);
            }
            if (wt.isCompleted()) talkMem.removeOneTalk(wt);
        }
    }

    //处理音频数据
    private void dealAudioDatagram() {
        PushUserUDKey pUdk=(PushUserUDKey)sourceMsg.getExtInfo();//发送该音频包的用户
        if (pUdk==null||!pUdk.isUser()) return;
        String talkerId=pUdk.getUserId();
        String talkId=sourceMsg.getTalkId();
        if (StringUtils.isEmptyOrWhitespaceOnly(talkId)) return;
        int seqNum=sourceMsg.getSeqNo();
        String objId=sourceMsg.getObjId();
        if (StringUtils.isEmptyOrWhitespaceOnly(objId)) return;

        int talkType=sourceMsg.getBizType();

        OneMeet om=(talkType==1?intercomMem.getOneMeet(objId):null);
        OneCall oc=(talkType==2?callingMem.getOneCall(objId):null);

        //组织回执消息
        MsgMedia retMm=new MsgMedia();
        retMm.setFromType(1);
        retMm.setToType(0);
        retMm.setMsgType(0);
        retMm.setAffirm(0);
        retMm.setBizType(sourceMsg.getBizType());
        retMm.setTalkId(talkId);
        retMm.setObjId(objId);
        retMm.setSeqNo(seqNum);

        if (talkType==1&&om==null) {//组对讲
            if (sourceMsg.isCtlAffirm()) {
                retMm.setReturnType(0x10);//对讲组内存数据不存在
                try {
                    globalMem.sendMem.putDeviceMsgMDA(pUdk, retMm);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return;
        } 
        if (talkType==2&&oc==null) { //电话
            if (sourceMsg.isCtlAffirm()) {
                retMm.setReturnType(0x10);//电话内存数据不存在
                try {
                    globalMem.sendMem.putDeviceMsgMDA(pUdk, retMm);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        //发送正常回执，这个有问题，还要考察
        if (sourceMsg.isCtlAffirm()) {
            retMm.setReturnType(0x01);
            try {
                globalMem.sendMem.putDeviceMsgMDA(pUdk, retMm);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //在对讲模式下：
//        if (om!=null&&om.getMeetType()==1&&(om.getSpeaker()==null||!om.getSpeaker().equals(pUdk))) return; //说话人不存在或不合法
//        if (oc!=null&&oc.getCallType()==1&&(oc.getSpeaker()==null||!om.getSpeaker().equals(pUdk))) return; //说话人不存在或不合法

        OneTalk wt=null;
        wt=talkMem.getOneTalk(talkId);
        
        if (wt==null) {
            wt=new OneTalk(talkId, pUdk, objId, talkType);
            talkMem.addOneTalk(wt);
        }
        TalkSegment ts=new TalkSegment();
        ts.setWt(wt);
        ts.setData(sourceMsg.getMediaData());
        ts.setSeqNum(seqNum);
        wt.addSegment(ts);

        if (talkType==1) {//对讲
            if (om.getEntryGroupUserMap()!=null&&!om.getEntryGroupUserMap().isEmpty()) {
                Map<String, PushUserUDKey> um=new HashMap<String, PushUserUDKey>();
                for (String k: om.getEntryGroupUserMap().keySet()) {
                    if (k.equals(pUdk.getUserId())) continue;
                    List<PushUserUDKey> al=sessionService.getActivedUserUDKs(k);
                    if (al!=null&&!al.isEmpty()) {
                        for (PushUserUDKey _pUdk: al) {
                            um.put(_pUdk.toString(), _pUdk);
                        }
                    }
                }
                if (!um.isEmpty()) ts.setSendUserMap(um);
            }
            om.addWt(pUdk, wt);
            om.setLastTalkTime(pUdk.getUserId());
            om.setLastUsedTime();
        } else {//电话
            PushUserUDKey otherUdk=oc.getOtherUdk(talkerId);
            if (otherUdk!=null) {
                Map<String, PushUserUDKey> um=new HashMap<String, PushUserUDKey>();
                um.put(otherUdk.toString(), otherUdk);
                ts.setSendUserMap(um);
            }
            if (talkerId.equals(oc.getCallerId())) oc.addCallerWt(wt);//呼叫者
            else
            if (talkerId.equals(oc.getCallederId())) oc.addCallederWt(wt);//被叫者
            oc.setLastTalkTime(pUdk.getUserId());
            oc.setLastUsedTime();
        }

        //发送广播消息，简单处理，只把这部分消息发给目的地，是声音数据文件
        MsgMedia bMsg=new MsgMedia();
        bMsg.setFromType(1);
        bMsg.setToType(0);
        bMsg.setMsgType(0);
        bMsg.setAffirm(1);
        bMsg.setBizType(sourceMsg.getBizType());
        bMsg.setTalkId(talkId);
        bMsg.setObjId(objId);
        bMsg.setSeqNo(seqNum);
        bMsg.setMediaData(sourceMsg.getMediaData());
        for (String k: ts.getSendUserMap().keySet()) {
            try {
                globalMem.sendMem.putDeviceMsgMDA(ts.getSendUserMap().get(k), bMsg);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //处理流数据
            ts.getSendFlagMap().put(k, 0);
            ts.getSendTimeMap().get(k).add(System.currentTimeMillis());
        }
    }
}