package com.woting.audioSNS.notify.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.notify.NotifyMessageConfig;
import com.woting.audioSNS.notify.mem.NotifyMemory;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.passport.UGA.service.GroupService;
import com.woting.push.PushConstants;
import com.woting.push.config.PushConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

public class DealNotifyMsg extends AbstractLoopMoniter<NotifyMessageConfig> {
    private Logger logger=LoggerFactory.getLogger(DealNotifyMsg.class);
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private NotifyMemory notifyMem=NotifyMemory.getInstance();
    private GroupService groupService;
    private PushConfig pConf=null;

    @SuppressWarnings("unchecked")
    public DealNotifyMsg(NotifyMessageConfig nmc, int index) {
        super(nmc);
        pConf=((CacheEle<PushConfig>)SystemCache.getCache(PushConstants.PUSH_CONF)).getContent();
        super.setName("通知消息处理线程"+index);
    }

    @Override
    public boolean initServer() {
        groupService=(GroupService)SpringShell.getBean("groupService");
        this.setLoopDelay(5);
        return groupService!=null;
    }

    @Override
    public void oneProcess() throws Exception {
        Message m=globalMem.receiveMem.takeTypeMsg("4");
        if (m==null||!(m instanceof MsgNormal)) return;

        MsgNormal mn=(MsgNormal)m;
        String tempStr=null;
        if (!mn.isAck()) {//非应答消息
            tempStr="处理通知消息["+toTypeName(mn)+"]";
            logger.debug(tempStr);
            (new DealNotifyProcess("{"+tempStr+"}处理线程", mn)).start();
        } else {
            tempStr="处理通知回执消息["+ackName(mn)+"]";
            logger.debug(tempStr);
            (new DealAckNotifyProcess("{"+tempStr+"}处理线程", mn)).start();
        }
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    class DealNotifyProcess extends Thread {
        private MsgNormal sourceMsg;//源消息
        protected DealNotifyProcess(String name, MsgNormal sourceMsg) {
            super.setName(name);
            this.sourceMsg=sourceMsg;
        }
        public void run() {
            try {
                //1-拿出消息体
                MapContent mc=(MapContent)sourceMsg.getMsgContent();
                if (mc==null||mc.getContentMap()==null||mc.getContentMap().size()==0) return;
                Map<String, Object> m=mc.getContentMap();

                //2.0-取出回复类型
                int affirmType=1;//默认为需要控制回复
                try {
                    affirmType=Integer.parseInt(m.get("_AFFIRMTYPE")==null?"":m.get("_AFFIRMTYPE")+"");
                } catch(Exception e) {}
                //2.1-取出目的信息
                String toGroups=(m.get("_TOGROUPS")==null?"":m.get("_TOGROUPS")+"");
                String toUsers=(m.get("_TOUSERS")==null?"":m.get("_TOUSERS")+"");
                String noUsers=(m.get("_NOUSERS")==null?"":m.get("_NOUSERS")+"");
                if (StringUtils.isNullOrEmptyOrSpace(toGroups)&&StringUtils.isNullOrEmptyOrSpace(toUsers)) return;
                //2.2-计算目的用户
                List<String> userIdL=new ArrayList<String>();
                String[] _tempSplit=null;
                boolean find=false;
                //2.2.1-处理组
                if (!StringUtils.isNullOrEmptyOrSpace(toGroups)) {
                    _tempSplit=toGroups.split(",");
                    for (String _ts: _tempSplit) {
                        //从内存取用户
                        Group g=globalMem.uANDgMem.getGroupById(_ts);
                        if (g==null) continue;
                        List<UserPo> ul=g.getUserList();
                        if (ul!=null&&!ul.isEmpty()) {
                            find=false;
                            for (UserPo up: ul) {
                                String newUserId=up.getUserId();
                                for (String _userId: userIdL) {
                                    if (newUserId.equals(_userId)) {
                                        find=true;
                                        break;
                                    }
                                }
                                if (!find) userIdL.add(newUserId);
                            }
                        }
                    }
                }
                //2.2.2-处理用户
                if (!StringUtils.isNullOrEmptyOrSpace(toUsers)) {
                    _tempSplit=toUsers.split(",");
                    for (String _ts: _tempSplit) {
                        find=false;
                        for (String _userId: userIdL) {
                            if (_ts.equals(_userId)) {
                                find=true;
                                break;
                            }
                        }
                        if (!find) userIdL.add(_ts);
                    }
                }
                //2.2.3-处理排除用户
                if (!StringUtils.isNullOrEmptyOrSpace(noUsers)) {
                    _tempSplit=toUsers.split(",");
                    for (String _ts: _tempSplit) {
                        for (int i=userIdL.size()-1; i>=0; i--) {
                            String _userId=userIdL.get(i);
                            if (_ts.equals(_userId)) userIdL.remove(i);
                        }
                    }
                }
                if (userIdL==null||userIdL.size()==0) return;

                //3构造新的消息
                //消息体处理
                Map<String, Object> newMsgContentMap=new HashMap<String, Object>();
                for (String k: m.keySet()) {
                    if (!k.startsWith("_")) newMsgContentMap.put(k, m.get(k));
                }
                MsgNormal nm=new MsgNormal();
                MapContent newMc=new MapContent(newMsgContentMap);
                nm.setMsgContent(newMc);
                nm.setBizType(4);
                nm.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                nm.setMsgType(0);
                nm.setAffirm(affirmType);
                nm.setFromType(0);
                nm.setToType(1);
                nm.setCmdType(sourceMsg.getCmdType());
                nm.setCommand(sourceMsg.getCommand());
                nm.setUserId(pConf.get_ServerType());
                nm.setDeviceId(pConf.get_ServerName());

                //4-发送消息
                for (String userId: userIdL) notifyMem.putNotifyMsg(userId, nm);

            } catch(Exception e) {
                logger.debug(StringUtils.getAllMessage(e));
            }
        }
    }

    class DealAckNotifyProcess extends Thread {
        private MsgNormal sourceMsg;//源消息
        protected DealAckNotifyProcess(String name, MsgNormal sourceMsg) {
            super.setName(name);
            this.sourceMsg=sourceMsg;
        }
        public void run() {
            try {
                List<PushUserUDKey> sendAckPUDkeyList=notifyMem.setBizReUdkANDGetNeedSendAckPudkList(sourceMsg);
                if (sendAckPUDkeyList!=null) {
                    for (PushUserUDKey pUdk: sendAckPUDkeyList) {
                        globalMem.sendMem.putDeviceMsg(pUdk, sourceMsg);
                    }
                }
            } catch(Exception e) {
                logger.debug(StringUtils.getAllMessage(e));
            }
        }
    }

    private String toTypeName(MsgNormal mn) {
        return "CmdType="+mn.getCmdType()+";Command="+mn.getCommand(); 
    }
    private String ackName(MsgNormal mn) {
        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(mn);
        return "FromUser={"+pUdk+"};MsgId="+mn.getReMsgId();
    }
}