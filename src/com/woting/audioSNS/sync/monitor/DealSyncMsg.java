package com.woting.audioSNS.sync.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.audioSNS.sync.SyncMessageConfig;
import com.woting.passport.UGA.persis.pojo.GroupPo;
import com.woting.passport.UGA.persis.pojo.UserPo;
import com.woting.passport.UGA.service.GroupService;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.ext.SpringShell;

/**
 * 主要处理用户组同步
 * @author wanghui
 */
public class DealSyncMsg extends AbstractLoopMoniter<SyncMessageConfig> {
    private Logger logger=LoggerFactory.getLogger(DealSyncMsg.class);
    private IntercomMemory interMem=IntercomMemory.getInstance();

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private GroupService groupService;

    public DealSyncMsg(SyncMessageConfig smc, int index) {
        super(smc);
        super.setName("同步消息处理线程"+index);
    }

    @Override
    public boolean initServer() {
        groupService=(GroupService)SpringShell.getBean("groupService");
        return groupService!=null;
    }

    @Override
    public void oneProcess() throws Exception {
        Message m=globalMem.receiveMem.pollTypeMsg("8");
        if (m==null||!(m instanceof MsgMedia)) return;

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
                String groupId=((MapContent)sourceMsg.getMsgContent()).get("GroupId")+"";
                if (!StringUtils.isNullOrEmptyOrSpace(groupId)) {
                    OneMeet om=interMem.getOneMeet(groupId);
                    if (om!=null) {
                        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==2) { //更改组信息
                            GroupPo gp=groupService.getGroupPo(groupId);
                            om.getGroup().buildFromPo(gp);
                        }
                        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==3) { //删除组
                            if (om.getSpeaker()==null) interMem.removeOneMeet(om.getGroupId());
                            else interMem.addToBeDelOneMeet(om);
                        }
                        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==4) { //加入组内成员
                            UserPo up=new UserPo();
                            up.fromHashMap((Map<String, Object>)((MapContent)sourceMsg.getMsgContent()).get("UserInfo"));
                            om.getGroup().addOneUser(up);
                        }
                        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==5) { //删除组内成员
                            String userId=((MapContent)sourceMsg.getMsgContent()).get("UserId")+"";
                            if (!StringUtils.isNullOrEmptyOrSpace(userId)) om.getGroup().delOneUser(userId);
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

    private String toTypeName(MsgNormal mn) {
        if (mn.getCommand()==2) return "加入组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"][UserId="+((Map<?,?>)((MapContent)mn.getMsgContent()).get("UserInfo")).get("UserId")+"]";
        if (mn.getCommand()==3) return "加入组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"][UserId="+((Map<?,?>)((MapContent)mn.getMsgContent()).get("UserInfo")).get("UserId")+"]";
        if (mn.getCommand()==4) return "加入组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"][UserId="+((Map<?,?>)((MapContent)mn.getMsgContent()).get("UserInfo")).get("UserId")+"]";
        if (mn.getCommand()==5) return "删除组成员::[groupId="+((MapContent)mn.getMsgContent()).get("GroupId")+"][UserId="+((MapContent)mn.getMsgContent()).get("UserId")+"]";
        return ""; 
    }
}
