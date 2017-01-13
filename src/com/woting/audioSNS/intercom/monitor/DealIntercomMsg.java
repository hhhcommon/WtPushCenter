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

    public DealIntercomMsg(IntercomConfig conf, int index) {
        super(conf);
        super.setName("对讲消息处理线程"+index);
        redisConn=(JedisConnectionFactory) SpringShell.getBean("redisConnFactory");
        this.setLoopDelay(10);
    }

    @Override
    public void oneProcess() throws Exception {
        MsgNormal sourceMsg=(MsgNormal)globalMem.receiveMem.takeTypeMsg("1");
        if (sourceMsg==null||sourceMsg.getBizType()!=1) return;

        PushUserUDKey pUdk=PushUserUDKey.buildFromMsg(sourceMsg);
        MsgNormal retMsg=MessageUtils.buildRetMsg(sourceMsg);

        //进入处理
        if (sourceMsg.getCmdType()==3&&sourceMsg.getCommand()==0) {
            List<Map<String, Object>> glm=intercomMem.getActiveGroupList(pUdk.getUserId());
            if (glm!=null&&!glm.isEmpty()) {
                retMsg.setFromType(1);
                retMsg.setToType(0);
                retMsg.setCmdType(3);
                retMsg.setCommand(0);
                Map<String, Object> dataMap=new HashMap<String, Object>();
                if (glm==null||glm.isEmpty()) retMsg.setReturnType(0);
                else {
                    retMsg.setReturnType(1);
                    dataMap.put("GroupList", glm);
                    MapContent mc=new MapContent(dataMap);
                    retMsg.setMsgContent(mc);
                }
                globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
            }
            return ;
        }

        String groupId=null;
        try {
            groupId=((MapContent)sourceMsg.getMsgContent()).get("GroupId")+"";
        } catch(Exception e) {}
        //不管任何消息，若groupId为空，则都认为是非法的消息，这类消息不进行任何处理，丢弃掉
        if (StringUtils.isNullOrEmptyOrSpace(groupId)) return;

        OneMeet om=intercomMem.getOneMeet(groupId);
        if (om!=null) {
            om.putPreMsg(sourceMsg);
            return ;
        }
        //组对象为空
        if (sourceMsg.getCmdType()==1&&sourceMsg.getCommand()==1) { //进入组
            RedisOperService roService=new RedisOperService(redisConn, 4);
            ExpirableBlockKey rLock=RedisBlockLock.lock(groupId, roService, new BlockLockConfig(5, 2, 0, 50));
            try {
                //创建组对象
                Group g=globalMem.uANDgMem.getGroupById(groupId);
                if (g==null) {
                    retMsg.setCommand(9);
                    retMsg.setReturnType(0x20);
                    Map<String, Object> dataMap=new HashMap<String, Object>();
                    dataMap.put("GroupId", groupId);
                    MapContent mc=new MapContent(dataMap);
                    retMsg.setMsgContent(mc);
                    globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
                    return;
                }
                om=new OneMeet(1, g);
                //加入内存
                int addFlag=intercomMem.addOneMeet(om);
                if (addFlag!=1) {
                    retMsg.setCommand(9);
                    retMsg.setReturnType(addFlag==0?0x81:0x82);
                    Map<String, Object> dataMap=new HashMap<String, Object>();
                    dataMap.put("GroupId", groupId);
                    MapContent mc=new MapContent(dataMap);
                    retMsg.setMsgContent(mc);
                    globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
                    return;
                }
                om.setStatus_1();

                om.putPreMsg(sourceMsg);
                //启动处理进程
                IntercomHandler interHandler=new IntercomHandler(conf, om);
                interHandler.setDaemon(true);
                interHandler.start();
            } finally {
                rLock.unlock();
                if (roService!=null) roService.close();
                roService=null;
            }
        } else { //不是进入组，则抛弃这个消息，并返回结果
            retMsg.setCmdType(3);
            retMsg.setCommand(0x30);
            retMsg.setReturnType(0x20);
            Map<String, Object> dataMap=new HashMap<String, Object>();
            dataMap.put("GroupId", groupId);
            dataMap.put("ServerMsg", "服务器处理进程不存在");
            MapContent mc=new MapContent(dataMap);
            retMsg.setMsgContent(mc);
            globalMem.sendMem.putDeviceMsg(pUdk, retMsg);
        }
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