package com.woting.audioSNS.sync.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.audioSNS.sync.SyncMessageConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;

/**
 * 主要处理用户组同步
 * @author wanghui
 */
public class DealSyncMsg extends AbstractLoopMoniter<SyncMessageConfig> {
    private Logger logger=LoggerFactory.getLogger(DealSyncMsg.class);
    private IntercomMemory interMem=IntercomMemory.getInstance();

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();

    public DealSyncMsg(SyncMessageConfig smc, int index) {
        super(smc);
        super.setName("同步消息处理线程"+index);
        this.setLoopDelay(5);
    }

    @Override
    public void oneProcess() throws Exception {
        Message m=globalMem.receiveMem.takeTypeMsg("8");
        if (m==null||!(m instanceof MsgNormal)) return;

        MsgNormal mn=(MsgNormal)m;
        if (!mn.isAck()) {//非应答消息
            String tempStr="处理同步消息["+toTypeName(mn)+"]";
            logger.debug(tempStr);
            (new DealSyncProcess("{"+tempStr+"}处理线程", mn)).start();
        }
    }

    //主要处理组信息同步
    class DealSyncProcess extends Thread {
        private MsgNormal sourceMsg;//源消息
        protected DealSyncProcess(String name, MsgNormal sourceMsg) {
            super.setName(name);
            this.sourceMsg=sourceMsg;
        }
        @SuppressWarnings("unchecked")
        public void run() {
            try {
                MapContent mc=(MapContent)sourceMsg.getMsgContent();
                if (mc==null||mc.getContentMap()==null||mc.getContentMap().size()==0) return;
                String groupId=mc.get("GroupId")+"";
                if (!StringUtils.isNullOrEmptyOrSpace(groupId)) {
                    if (sourceMsg.getCmdType()==2) {
                        if (sourceMsg.getCommand()==2) { //更改组信息
                            globalMem.uANDgMem.updateGroup(groupId);
                        } else
                        if (sourceMsg.getCommand()==3) { //删除组
                            globalMem.uANDgMem.delGroup(groupId);
                            OneMeet om=interMem.getOneMeet(groupId);
                            if (om!=null) {
                                if (om.getSpeaker()==null) interMem.removeOneMeet(om.getGroupId());
                                else interMem.addToBeDelOneMeet(om);
                            }
                        } else
                        if (sourceMsg.getCommand()==4) { //加入组内成员
                            Map<String, Object> um=(Map<String, Object>)mc.get("UserInfo");
                            if (um!=null&&!StringUtils.isNullOrEmptyOrSpace((String)um.get("userId"))) {
                                globalMem.uANDgMem.addUserToGroup(groupId, (String)um.get("userId"));
                            }
                        } else
                        if (sourceMsg.getCommand()==5) { //删除组内成员
                            Map<String, Object> um=(Map<String, Object>)mc.get("UserInfo");
                            if (um!=null&&!StringUtils.isNullOrEmptyOrSpace((String)um.get("userId"))) {
                                globalMem.uANDgMem.delUserFromGroup(groupId, (String)um.get("userId"));
                            }
                        }
                    }
                }
            } catch(Exception e) {
                logger.debug(StringUtils.getAllMessage(e));
            }
        }
    }
    @Override
    public boolean canContinue() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private String toTypeName(MsgNormal mn) {
        MapContent mc=(MapContent)mn.getMsgContent();
        Map<String, Object> um=((Map<String, Object>)mc.get("UserInfo"));
        if (mn.getCommand()==2) return "加入组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"]"+(um==null?"":"[UserId="+um.get("userId")+"]");
        if (mn.getCommand()==3) return "加入组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"]"+(um==null?"":"[UserId="+um.get("userId")+"]");
        if (mn.getCommand()==4) return "加入组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"]"+(um==null?"":"[UserId="+um.get("userId")+"]");
        if (mn.getCommand()==5) return "删除组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"][UserId="+mc.get("UserId")+"]";
        return ""; 
    }
}
