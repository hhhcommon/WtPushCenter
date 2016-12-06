package com.woting.audioSNS.intercom.monitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import com.spiritdata.framework.core.lock.BlockLockConfig;
import com.spiritdata.framework.core.lock.ExpirableBlockKey;
import com.spiritdata.framework.ext.redis.lock.RedisBlockLock;
import com.spiritdata.framework.ext.spring.redis.RedisOperService;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.intercom.IntercomConfig;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.intercom.model.OneMeet;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.service.GroupService;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.AbstractLoopMoniter;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

public class DealIntercomMsg extends AbstractLoopMoniter<IntercomConfig> {
    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private IntercomMemory intercomMem=IntercomMemory.getInstance();
    private JedisConnectionFactory redisConn;

    private GroupService groupService;

    public DealIntercomMsg(IntercomConfig conf, int index) {
        super(conf);
        super.setName("对讲处理线程"+index);
        redisConn=(JedisConnectionFactory) SpringShell.getBean("redisConnFactory");
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

        String groupId=null;
        try {
            groupId=((MapContent)sourceMsg.getMsgContent()).get("GroupId")+"";
        } catch(Exception e) {}
        //不管任何消息，若groupId为空，则都认为是非法的消息，这类消息不进行任何处理，丢弃掉
        if (StringUtils.isNullOrEmptyOrSpace(groupId)) return;

        OneMeet om=intercomMem.getOneMeet(groupId);
        if (om==null) {
            if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==1) {//进入组
                RedisOperService roService=new RedisOperService(redisConn, 4);
                ExpirableBlockKey rLock=RedisBlockLock.lock(groupId, roService, new BlockLockConfig(5, 2, 0, 50));
                try {
                    om=intercomMem.getOneMeet(groupId);
                    if (om!=null) om.addPreMsg(sourceMsg);
                    else {
                        //创建组对象
                        Group g=groupService.getGroup(groupId);
                        if (g==null) {
                            MsgNormal retMsg=MessageUtils.buildRetMsg(sourceMsg);
                            PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(sourceMsg);
                            retMsg.setCommand(9);
                            retMsg.setReturnType(0x20);
                            Map<String, Object> dataMap=new HashMap<String, Object>();
                            dataMap.put("GroupId", groupId);
                            MapContent mc=new MapContent(dataMap);
                            retMsg.setMsgContent(mc);
                            globalMem.sendMem.addUserMsg(pUdk, retMsg);
                            return;
                        }
                        om=new OneMeet(1, g);
                        om.addPreMsg(sourceMsg);
                        //加入内存
                        int addFlag=intercomMem.addOneMeet(om);
                        if (addFlag!=1) {
                            MsgNormal retMsg=MessageUtils.buildRetMsg(sourceMsg);
                            PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(sourceMsg);
                            retMsg.setCommand(9);
                            retMsg.setReturnType(addFlag==0?0x81:0x82);
                            Map<String, Object> dataMap=new HashMap<String, Object>();
                            dataMap.put("GroupId", groupId);
                            MapContent mc=new MapContent(dataMap);
                            retMsg.setMsgContent(mc);
                            globalMem.sendMem.addUserMsg(pUdk, retMsg);
                            return;
                        }
                        om.setStatus_1();
                        //启动处理进程
                        IntercomHandler interHandler=new IntercomHandler(conf, om);
                        interHandler.setDaemon(true);
                        interHandler.start();
                    }
                } finally {
                    rLock.unlock();
                    if (roService!=null) roService.close();
                    roService=null;
                }
            } else { //不是进入组，则抛弃这个消息，并返回结果
                MsgNormal retMsg=MessageUtils.buildRetMsg(sourceMsg);
                PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(sourceMsg);
                retMsg.setCmdType(3);
                retMsg.setCommand(sourceMsg.getCommand());
                retMsg.setReturnType(sourceMsg.getCmdType());
                Map<String, Object> dataMap=new HashMap<String, Object>();
                dataMap.put("GroupId", groupId);
                MapContent mc=new MapContent(dataMap);
                retMsg.setMsgContent(mc);
                globalMem.sendMem.addUserMsg(pUdk, retMsg);
            }
        } else om.addPreMsg(sourceMsg);
    }

    @Override
    public void destroyServer() {
        //销毁所有消息处理线程
        List<IntercomHandler> ihL=intercomMem.getIntercomHanders();
        if (ihL!=null&&!ihL.isEmpty()) {
            for (IntercomHandler ih:ihL) ih.stopServer();

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