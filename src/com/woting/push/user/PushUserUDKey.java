package com.woting.push.user;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.spiritdata.framework.util.StringUtils;
import com.woting.passport.session.key.UserDeviceKey;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;

public class PushUserUDKey extends UserDeviceKey implements Serializable {
    private static final long serialVersionUID=-1794652738025588641L;

    public PushUserUDKey() {
        super();
    }
    public PushUserUDKey(UserDeviceKey udk) {
        super();
        this.setDeviceId(udk.getDeviceId());
        this.setPCDType(udk.getPCDType());
        this.setUserId(udk.getUserId());
    }

    /**
     * 判断此Key是否有效
     * @return
     */
    public boolean isValidate() {
        return this.PCDType>0&&!StringUtils.isNullOrEmptyOrSpace(this.deviceId);
    }

    /**
     * 判断此Key是否是User用户
     * @return
     */
    public boolean isUser() {
        return !StringUtils.isNullOrEmptyOrSpace(this.userId);
    }

    /**
     * 是否是匿名用户（游客）
     * @return
     */
    public boolean isAnonymous() {
        return !isUser();
    }

    /**
     * 是否是设备用户，设备出厂时的自动注册的用户
     * @return
     */
    public boolean isDeviceUser() {
        if (StringUtils.isNullOrEmptyOrSpace(this.deviceId)) return false;
        if (this.PCDType==2&&this.deviceId.equals(this.userId)) return true;
        return false;
    }

    /**
     * 从消息体获取用户设备信息
     * @param msg 消息，注意，必须是一般消息
     * @return 返回移动端设备用户key
     */
    public static PushUserUDKey buildFromMsg(Message msg) {
        if (!(msg instanceof MsgNormal)) return null;
        PushUserUDKey ret=new PushUserUDKey();
        MsgNormal _m=(MsgNormal)msg;
        ret.setUserId(_m.getUserId());
        ret.setPCDType(_m.getPCDType());
        ret.setDeviceId(_m.getIMEI());
        return ret;
    }

    @Override
    public Map<String, Object> toHashMapAsBean() {
        Map<String, Object> retMap=new HashMap<String, Object>();
        if (!StringUtils.isNullOrEmptyOrSpace(deviceId)) retMap.put("IMEI", deviceId);
        if (!StringUtils.isNullOrEmptyOrSpace(userId)) retMap.put("UserId", userId);
        if (PCDType>0) retMap.put("PCDType", PCDType);
        return retMap.isEmpty()?null:retMap;
    }
}