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
import com.woting.audioSNS.mediaflow.MediaflowConfig;
import com.woting.audioSNS.mediaflow.mem.TalkMemory;
import com.woting.audioSNS.mediaflow.model.TalkSegment;
import com.woting.audioSNS.mediaflow.model.WholeTalk;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

public class DealMediaflowMsg extends AbstractLoopMoniter<MediaflowConfig> {
    private Logger logger=LoggerFactory.getLogger(DealMediaflowMsg.class);

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private IntercomMemory intercomMem=IntercomMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();
    private TalkMemory talkMem=TalkMemory.getInstance();

    private SessionService sessionService=null;

    /**
     * 给线程起一个名字的构造函数
     * @param name 线程名称
     */
    public DealMediaflowMsg(MediaflowConfig mfc, int index) {
        super(mfc);
        super.setName("流数据处理线程"+index);
    }

    @Override
    public boolean initServer() {
        sessionService=(SessionService)SpringShell.getBean("sessionService");
        return true;
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    @Override
    public void oneProcess() throws Exception {
        Message m=globalMem.receiveMem.pollTypeMsg("media");
        if (m==null||!(m instanceof MsgMedia)) return;

        MsgMedia mm=(MsgMedia)m;
        //暂存，解码
        String tempStr=null;
        if (!mm.isAck()) {//收到音频包
            tempStr="处理流数据[SeqId="+mm.getSeqNo()+"]-收到音频数据包::(User="+mm.getFromType()+";TalkId="+mm.getTalkId();
            logger.debug(tempStr);
            (new ReceiveAudioDatagram("{"+tempStr+"}处理线程", mm)).start();
        } else {//收到回执包
            tempStr="处理流数据[SeqId="+mm.getSeqNo()+"]-收到音频回执包::(User="+mm.getFromType()+";TalkId="+mm.getTalkId();
            logger.debug(tempStr);
            (new ReceiveAudioAnswer("{"+tempStr+"}处理线程", mm)).start();
        }
    }

    //收到音频数据包
    class ReceiveAudioDatagram extends Thread {
        private MsgMedia sourceMsg;//源消息
        protected ReceiveAudioDatagram(String name, MsgMedia sourceMsg) {
            super.setName(name);
            this.sourceMsg=sourceMsg;
        }
        public void run() {
            PushUserUDKey pUdk=(PushUserUDKey)sourceMsg.getExtInfo();//发送该音频包的用户
            if (pUdk==null||!pUdk.isUser()) return;
            String talkerId=pUdk.getUserId();
            String talkId=sourceMsg.getTalkId();
            if (StringUtils.isEmptyOrWhitespaceOnly(talkId)) return;
            int seqNum=sourceMsg.getSeqNo();
            String objId=sourceMsg.getObjId();
            if (StringUtils.isEmptyOrWhitespaceOnly(objId)) return;

            int talkType=sourceMsg.getBizType();

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

            OneMeet om=null;
            OneCall oc=null;
            if (talkType==1) {//组对讲
                om=intercomMem.getOneMeet(objId);
                if (om==null) {
                    retMm.setReturnType(0x10);//对讲组内存数据不存在
                    globalMem.sendMem.addUnionUserMsg(pUdk, retMm);
                    return;
                }
            } else {//电话
                oc=callingMem.getOneCall(objId);
                if (oc==null) {
                    retMm.setReturnType(0x10);//电话内存数据不存在
                    globalMem.sendMem.addUnionUserMsg(pUdk, retMm);
                    return;
                }
            }
            WholeTalk wt=null;
            wt=talkMem.getWholeTalk(talkId);
            if (wt==null) {
                wt=new WholeTalk();
                wt.setTalkId(talkId);
                wt.setTalkerMk(pUdk);
                wt.setObjId(objId);
                wt.setTalkType(talkType);
                talkMem.addWholeTalk(wt);
                wt.startMonitor(wt);
                //加入电话控制中
                if (talkType==2) {
                    if (talkerId.equals(oc.getCallerId())) oc.addCallerWt(wt);//呼叫者
                    else
                    if (talkerId.equals(oc.getCallederId())) oc.addCallederWt(wt);//被叫者
                }
                //加入对讲中
                if (talkType==1) om.addWt(pUdk, wt);
            }
            TalkSegment ts=new TalkSegment();
            ts.setWt(wt);
            ts.setData(sourceMsg.getMediaData());
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
            } else {//电话
                PushUserUDKey otherUdk=oc.getOtherUdk(talkerId);
                if (otherUdk!=null) {
                    Map<String, PushUserUDKey> um=new HashMap<String, PushUserUDKey>();
                    um.put(otherUdk.toString(), otherUdk);
                    ts.setSendUserMap(um);
                }
            }
            ts.setSeqNum(seqNum);
            wt.addSegment(ts);
            if (talkType==1) {
                om.setLastTalkTime(pUdk.getUserId());
                om.setLastUsedTime();
            } else oc.setLastUsedTime();

            //发送正常回执
            if (sourceMsg.isCtlAffirm()) {
                retMm.setReturnType(0x01);
                globalMem.sendMem.addUnionUserMsg(pUdk, retMm);
            }

//            if (new String(ts.getData()).equals("####")) System.out.println("deCode:::====="+new String(ts.getData()));
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
                globalMem.sendMem.addUnionUserMsg(ts.getSendUserMap().get(k), bMsg);
                //处理流数据
                ts.getSendFlagMap().put(k, 0);
                ts.getSendTimeMap().get(k).add(System.currentTimeMillis());
            }
        }
    }

    //收到音频回执包
    class ReceiveAudioAnswer extends Thread {
        private MsgMedia sourceMsg;//源消息
        protected ReceiveAudioAnswer(String name, MsgMedia sourceMsg) {
            super.setName(name);
            this.sourceMsg=sourceMsg;
        }
        public void run() {
            PushUserUDKey pUdk=(PushUserUDKey)sourceMsg.getExtInfo();
            if (pUdk==null||!pUdk.isUser()) return;
            String talkerId=pUdk.getUserId();
            String talkId=sourceMsg.getTalkId();
            if (StringUtils.isEmptyOrWhitespaceOnly(talkerId)) return;
            int seqNum=sourceMsg.getSeqNo();
            if (seqNum<0) return;
            String groupId=sourceMsg.getObjId();
            if (StringUtils.isEmptyOrWhitespaceOnly(groupId)) return;
            WholeTalk wt=talkMem.getWholeTalk(talkId);
            if (wt!=null) {
                if (sourceMsg.getReturnType()==1) {
                    TalkSegment ts=wt.getTalkData().get(Math.abs(seqNum));
                    if (ts!=null&&ts.getSendFlagMap().get(pUdk.toString())!=null) ts.getSendFlagMap().put(pUdk.toString(), 2);
                }
                if (wt.isSendCompleted()) talkMem.removeWholeTalk(wt);
            }
        }
    }
}