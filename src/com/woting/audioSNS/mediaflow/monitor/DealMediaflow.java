package com.woting.audioSNS.mediaflow.monitor;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mysql.jdbc.StringUtils;
import com.spiritdata.framework.FConstants;
import com.spiritdata.framework.core.cache.SystemCache;
import com.woting.audioSNS.calling.CallingConfig;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.calling.model.OneCall;
import com.woting.audioSNS.calling.monitor.CallHandler;
import com.woting.audioSNS.mediaflow.MediaflowConfig;
import com.woting.audioSNS.mediaflow.mem.TalkMemory;
import com.woting.audioSNS.mediaflow.model.CompareAudioFlowMsg;
import com.woting.audioSNS.mediaflow.model.TalkSegment;
import com.woting.audioSNS.mediaflow.model.WholeTalk;
import com.woting.push.core.mem.TcpGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.passport.UGA.service.UserService;

public class DealMediaflow extends AbstractLoopMoniter<MediaflowConfig> {
    private Logger logger=LoggerFactory.getLogger(DealMediaflow.class);

    private TcpGlobalMemory globalMem=TcpGlobalMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();

    private TalkMemory talkMem=TalkMemory.getInstance();
    private SessionService sessionService=null;
    private UserService userService=null;

    /**
     * 给线程起一个名字的构造函数
     * @param name 线程名称
     */
    public DealMediaflow(MediaflowConfig mfc, int index) {
        super(mfc);
        super.setName("流数据处理线程"+index);
        this.setLoopDelay(10);
    }

    @Override
    public boolean initServer() {
        sessionService=(SessionService)SpringShell.getBean("sessionService");
        userService=(UserService)SpringShell.getBean("userService");
        return sessionService!=null&&userService!=null;
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

//            GroupInterCom gic=null;
            OneCall oc=null;
            if (talkType==1) {//组对讲
//                gic=gmm.getGroupInterCom(objId);
//                if (gic==null||gic.getSpeaker()==null||!gic.getSpeaker().getUserId().equals(talkerId)) {
////                    if (gic==null||gic.getSpeaker()==null||!gic.getSpeaker().getUserId().equals(talkerId)) {
////                    retMsg.setReturnType("1002");
////                    pmm.getSendMemory().addUniqueMsg2Queue(mk, retMsg, new CompareAudioFlowMsg());
//                    return;
//                }
            } else {//电话
                oc=callingMem.getCallData(objId);
                if (oc==null) {
//                    retMsg.setReturnType("1003");
//                    pmm.getSendMemory().addUniqueMsg2Queue(mk, retMsg, new CompareAudioFlowMsg());
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
            }
            TalkSegment ts=new TalkSegment();
            ts.setWt(wt);
            ts.setData(sourceMsg.getMediaData());
            if (talkType==1) ;//ts.setSendUserMap(gic.getEntryGroupUserMap());//组对讲
            else {//电话
                UserPo u=null; 
                PushUserUDKey otherUdk=oc.getOtherUdk(talkerId);
                otherUdk=(PushUserUDKey)sessionService.getActivedUserUDK(otherUdk.getUserId(), otherUdk.getPCDType());
                if (pUdk!=null) u=userService.getUserById(otherUdk.getUserId());
                if (u!=null) {
                    Map<String, UserPo> um=new HashMap<String, UserPo>();
                    um.put(pUdk.toString(), u);
                    ts.setSendUserMap(um);
                }
            }
            ts.setSeqNum(seqNum);
            wt.addSegment(ts);
            if (talkType==1) ;//gic.setLastTalkTime(talkerId);
            else oc.setLastUsedTime();

            //发送正常回执
//            retMsg.setReturnType("1001");
//            pmm.getSendMemory().addUniqueMsg2Queue(mk, retMsg, new CompareAudioFlowMsg());

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
                String _sp[]=k.split("::");
                pUdk=new PushUserUDKey();
                pUdk.setDeviceId(_sp[0]);
                pUdk.setPCDType(Integer.parseInt(_sp[1]));
                pUdk.setUserId(_sp[2]);
                globalMem.sendMem.addUnionUserMsg(pUdk, bMsg, new CompareAudioFlowMsg());
                //处理流数据
                ts.getSendFlagMap().put(k, 0);
                ts.getSendTimeMap().get(k).add(System.currentTimeMillis());
            }

            //看是否是结束包
            if (wt.isReceiveCompleted()) {
                if (talkType==1) {
                    //gic.sendEndPTT();
                    //gic.delSpeaker(talkerId);
                }
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

            int talkType=sourceMsg.getBizType();

            WholeTalk wt=talkMem.getWholeTalk(talkId);
            if (wt!=null) {
                if (sourceMsg.getReturnType()==1) {
                    TalkSegment ts=wt.getTalkData().get(Math.abs(seqNum));
                    if (ts!=null&&ts.getSendFlagMap().get(pUdk.toString())!=null) ts.getSendFlagMap().put(pUdk.toString(), 2);
                }
                if (wt.isSendCompleted()) {
                    talkMem.removeWholeTalk(wt);
                    //发送结束对讲消息
                    if (talkType==1)  {
//                        GroupInterCom gic=gmm.getGroupInterCom(groupId);
//                        if (gic!=null&&gic.getSpeaker()!=null) {
//                            gic.sendEndPTT();
//                            gic.delSpeaker(talkerId);
//                        }
                    }
                }
            }
        }
    }
}