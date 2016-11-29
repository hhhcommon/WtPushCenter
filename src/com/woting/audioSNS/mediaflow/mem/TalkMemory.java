package com.woting.audioSNS.mediaflow.mem;

import java.util.concurrent.ConcurrentHashMap;
import com.woting.audioSNS.mediaflow.model.WholeTalk;

public class TalkMemory {
    //java的占位单例模式===begin
    private static class InstanceHolder {
        public static TalkMemory instance=new TalkMemory();
    }
    public static TalkMemory getInstance() {
        return InstanceHolder.instance;
    }
    //java的占位单例模式===end

    protected ConcurrentHashMap<String, WholeTalk> talkMap;//对讲组信息Map
    private TalkMemory() {
        this.talkMap=new ConcurrentHashMap<String, WholeTalk>();
    }

    /**
     * 获得一个对话数据
     * @param talkId 对话Id
     * @return 一个对话数据
     */
    public WholeTalk getWholeTalk(String talkId) {
        return talkMap.get(talkId);
    }

    /**
     * 删除一个对话数据
     * @param talkId 对话Id
     */
    public void removeWholeTalk(String talkId) {
        talkMap.remove(talkId);
    }

    /**
     * 删除一个对话数据
     * @param wt 对话数据
     */
    public void removeWholeTalk(WholeTalk wt) {
        talkMap.remove(wt.getTalkId());
    }

    /**
     * 加入内存
     * @param wt 对话数据
     * @return 返回内存中与这个对讲对应的结构，若内存中已经存在，则返回内存中的结构，否则返回这个新结构
     */
    public void addWholeTalk(WholeTalk wt) {
        talkMap.put(wt.getTalkId(), wt);
    }

    /**
     * 清除组对讲内存
     */
    public void clean() {
//        if (talkMap!=null&&!talkMap.isEmpty()) {
//            Map<String, Object> dataMap;
//            MsgNormal exitPttMsg;
//            MobileUDKey mUdk;
//
//            PushMemoryManage pmm=PushMemoryManage.getInstance();
//            for (String k: talkMap.keySet()) {
//                WholeTalk wt=talkMap.get(k);
//                if (wt.getTalkType()==1) {//对讲
//                    GroupMemoryManage gmm=GroupMemoryManage.getInstance();
//                    GroupInterCom gic=gmm.getGroupInterCom(wt.getObjId());
//                    //判断对讲是否结束
//                    boolean talkEnd=false;
//                    talkEnd=wt.isSendCompleted()||gic==null||gic.getSpeaker()==null;
//                    if (talkEnd) {
//                        this.removeWholeTalk(wt);//清除语音内存
//                        //发广播消息，推出PTT
//                        if (gic.getSpeaker()!=null) {
//                            //广播结束消息
//                            exitPttMsg=new MsgNormal();
//                            exitPttMsg.setFromType(1);
//                            exitPttMsg.setToType(0);
//                            exitPttMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
//                            exitPttMsg.setMsgType(0);
//                            exitPttMsg.setBizType(1);
//                            exitPttMsg.setCmdType(2);
//                            exitPttMsg.setCommand(0x20);
//                            dataMap=new HashMap<String, Object>();
//                            dataMap.put("GroupId", wt.getObjId());
//                            dataMap.put("TalkUserId", wt.getTalkerId());
//                            MapContent mc=new MapContent(dataMap);
//                            exitPttMsg.setMsgContent(mc);
//
//                            //发送广播消息
//                            Map<String, UserPo> entryGroupUsers=gic.getEntryGroupUserMap();
//                            for (String _k: entryGroupUsers.keySet()) {
//                                String _sp[]=_k.split("::");
//                                mUdk=new MobileUDKey();
//                                mUdk.setDeviceId(_sp[0]);
//                                mUdk.setPCDType(Integer.parseInt(_sp[1]));
//                                mUdk.setUserId(_sp[2]);
//                                pmm.getSendMemory().addUniqueMsg2Queue(mUdk, exitPttMsg, new CompareGroupMsg());
//                            }
//                        }
//                        gic.delSpeaker(gic.getSpeaker()==null?null:gic.getSpeaker().getUserId());
//                        this.removeWholeTalk(wt);
//                    }
//                }
//            }
//        }
    }

    /**
     * 清除电话通话内容
     * @param callId 通话id
     */
    public void cleanCallData(String callId) {
        if (talkMap!=null&&!talkMap.isEmpty()) {
            for (String k: talkMap.keySet()) {
                WholeTalk wt=talkMap.get(k);
                if (wt.getTalkType()==2&&wt.getObjId().equals(callId)) {
                    this.removeWholeTalk(wt);
                }
            }
        }
    }

    /**
     * 清除电话通话内容
     * @param callId 通话id
     */
    public void cleanIntercomData(String talkId, String groupId) {
        if (talkMap!=null&&!talkMap.isEmpty()) {
            for (String k: talkMap.keySet()) {
                WholeTalk wt=talkMap.get(k);
                if (wt.getTalkType()==1&&wt.getObjId().equals(groupId)) {
                    this.removeWholeTalk(wt);
                }
            }
        }
    }
}