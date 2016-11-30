package com.woting.audioSNS.intercom.monitor;

import java.util.List;

import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.IntercomConfig;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.service.GroupService;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.ext.SpringShell;

public class DealIntercom extends AbstractLoopMoniter<IntercomConfig> {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private IntercomMemory intercomMem=IntercomMemory.getInstance();

    private GroupService groupService;

    public DealIntercom(IntercomConfig conf, int index) {
        super(conf);
        super.setName("对讲处理线程"+index);
        this.setLoopDelay(10);
    }

    @Override
    public boolean initServer() {
        groupService=(GroupService)SpringShell.getBean("groupService");
        return groupService!=null;
    }

    @Override
    public void oneProcess() throws Exception {
        MsgNormal sourceMsg=(MsgNormal)globalMem.receiveMem.pollTypeMsg("1");
        if (sourceMsg==null||sourceMsg.getBizType()!=1) return;

        if (sourceMsg.getCmdType()==0) { //进入绑定
            
        } else {
            String groupId=null;
            try {
                groupId=((MapContent)sourceMsg.getMsgContent()).get("GroupId")+"";
            } catch(Exception e) {}
            //不管任何消息，若groupId为空，则都认为是非法的消息，这类消息不进行任何处理，丢弃掉
            if (StringUtils.isNullOrEmptyOrSpace(groupId)) return;

            OneMeet om=intercomMem.getOneMeet(groupId);
            if (om!=null) om.addPreMsg(sourceMsg);
            else {
                //创建组对象
                Group g=groupService.getGroup(groupId);
                if (g==null) return;
                om=new OneMeet(1, g);
                om.addPreMsg(sourceMsg);
                //启动处理进程
                IntercomHandler interHandler=new IntercomHandler(conf, om);
                interHandler.setDaemon(true);
                interHandler.start();
            }
        }
    }

    @Override
    public void destroyServer() {
        //销毁所有消息处理线程
        List<IntercomHandler> ihL=intercomMem.getIntercomHanders();
        if (ihL!=null&&!ihL.isEmpty()) {
            for (IntercomHandler ih:ihL) {
                ih.stopServer();
            }
            boolean allClosed=false;
            int i=0;
            while (i++<10&&!allClosed) {
                allClosed=true;
                for (IntercomHandler ih:ihL) {
                    allClosed=ih.isStoped();
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