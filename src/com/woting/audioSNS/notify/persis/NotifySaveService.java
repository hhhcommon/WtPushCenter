package com.woting.audioSNS.notify.persis;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.spiritdata.framework.core.dao.mybatis.MybatisDAO;
import com.spiritdata.framework.core.model.BaseObject;
import com.spiritdata.framework.core.model.Page;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.SequenceUUID;
import com.woting.audioSNS.notify.model.OneNotifyMsg;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgNormal;

@Service
public class NotifySaveService {
    @Resource(name="defaultDAO")
    private MybatisDAO<BaseObject> notifyMsgDao;

    @PostConstruct
    public void initParam() {
        notifyMsgDao.setNamespace("NOTIFYMSG");
    }

    public void insert(Map<String, Object> m) {
        m.put("id", SequenceUUID.getPureUUID());
        notifyMsgDao.insert(m);
    }

    public void update(Map<String, Object> m) {
        notifyMsgDao.update(m);
    }

    /**
     * 从数据库中读取未完成的通知消息，并填入参数中
     * @param userNotifyMap 被填入的对象
     */
    public void fillNotifyFromDB(Map<String, List<OneNotifyMsg>> userNotifyMap, int expLmtPerUser) {
        if (userNotifyMap==null) return;

        int i=1, pageSize=10000;
        Page<Map<String, Object>> msgPage=notifyMsgDao.pageQueryAutoTranform(null, "getNoDealList", expLmtPerUser, i++, pageSize);
        boolean hasData=(msgPage!=null&&msgPage.getDataCount()==pageSize);
        if (msgPage!=null&&!msgPage.getResult().isEmpty()) fillData(msgPage.getResult().toArray(), userNotifyMap, expLmtPerUser);

        while (hasData) {
            msgPage=notifyMsgDao.pageQueryAutoTranform(null, "getNoDealList", expLmtPerUser, i++, pageSize);
            hasData=(msgPage!=null&&msgPage.getDataCount()==pageSize);
            if (msgPage!=null&&!msgPage.getResult().isEmpty()) fillData(msgPage.getResult().toArray(), userNotifyMap, expLmtPerUser);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void fillData(Object[] array, Map<String, List<OneNotifyMsg>> userNotifyMap, int expLmtPerUser) {
        Map<String, Object> oneData=null;
        String userId=null, thisUserId=null, tmpStr=null;
        List<OneNotifyMsg> userNotifyList=null;
        for (Object _o: array) {
            oneData=(Map<String, Object>)_o;
            try {
                if (Integer.parseInt(""+oneData.get("sendAllNum"))>=expLmtPerUser) continue;
            } catch(Exception e) {}
            thisUserId=(oneData.get("toUserId")==null?"":oneData.get("toUserId")+"");
            if (!thisUserId.equals(userId)) {
                userId=thisUserId;
                userNotifyList=userNotifyMap.get(userId);
                if (userNotifyList==null) {
                    userNotifyList=new ArrayList<OneNotifyMsg>();
                    userNotifyMap.put(userId, userNotifyList);
                }
            }
            tmpStr=oneData.get("msgJson")+"";
            Map nmM=(Map)JsonUtils.jsonToObj(tmpStr, Map.class);
            MsgNormal nmn=MessageUtils.buildNoramlMsg(nmM);

            OneNotifyMsg onm=new OneNotifyMsg(thisUserId, nmn);
            onm.setBizReUdk(null);
            Timestamp tp=null;
            try { tp=(Timestamp)oneData.get("sendTime"); } catch(Exception e) {};
            if (tp!=null) onm.setFirstSendTime(tp.getTime());
            onm.setUserId(thisUserId);
            tmpStr=oneData.get("sendInfoJson")==null?null:oneData.get("sendInfoJson")+"";
            onm.setSendMapFromJson(tmpStr);
            userNotifyList.add(onm);
        }
    }
}