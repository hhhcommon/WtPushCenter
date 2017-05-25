package com.woting;

import java.util.HashMap;
import java.util.Map;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.jsonconf.JsonConfig;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.SequenceUUID;
import com.woting.push.PushConstants;
import com.woting.push.config.ConfigLoadUtils;
import com.woting.push.config.UrlConvertConfig;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;

public class OnlyTest {
    public static void main(String[] args) {
        String lineSeparator="";//java.security.AccessController.doPrivileged(new sun.security.action.GetPropertyAction("line.separator"));
        System.out.println(lineSeparator.length());
        System.out.println(lineSeparator);
        byte[] b=lineSeparator.getBytes();
        for (int i=0; i<b.length; i++) {
            System.out.println((b[i]));
        }
//
//        String a="null::201::protocol=http:::4";
//        String[] _split=a.split("::");
//        String c=_split[2];
//        int d=Integer.parseInt(_split[3]);
//        System.out.println(c+"::"+d);
        //通知消息
        MsgNormal nMsg=new MsgNormal();
        nMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
        nMsg.setFromType(0);
        nMsg.setToType(0);
        nMsg.setMsgType(0);
        nMsg.setAffirm(1);
        nMsg.setBizType(0x04);
        nMsg.setCmdType(1);
        nMsg.setCommand(1);
        nMsg.setPCDType(0);
        nMsg.setUserId("AppCM");
        nMsg.setDeviceId("AppConMnServer");
        Map<String, Object> dataMap=new HashMap<String, Object>();
        dataMap.put("InviteMsg", "测试消息测试消息");
        dataMap.put("InviteTime", System.currentTimeMillis());
        dataMap.put("UrlTest", "##userimg##/test.html");
        dataMap.put("_TOGROUPS", "YUYINTest00");
        MapContent mc=new MapContent(dataMap);
        nMsg.setMsgContent(mc);

        System.out.println(JsonUtils.objToJson(nMsg));
        byte[] b1;
        try {
            JsonConfig jc=new JsonConfig("D:/workIDE/projects/pushcenter/conf/config.jconf");
            UrlConvertConfig ucc=ConfigLoadUtils.getUrlConvertConfig(jc);
            SystemCache.setCache(new CacheEle<UrlConvertConfig>(PushConstants.URLCONVERT_CONF, "URL转化配置", ucc));
            b1 = nMsg.toBytes();
            MsgNormal nMsg2=new MsgNormal(b1);
            System.out.println(JsonUtils.objToJson(nMsg2));
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}