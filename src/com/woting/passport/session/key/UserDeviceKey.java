package com.woting.passport.session.key;

import java.io.Serializable;

import com.spiritdata.framework.core.model.BaseObject;

/**
 * 用户设备Key对象
 * @author wanghui
 */
public class UserDeviceKey extends BaseObject implements Serializable {
    private static final long serialVersionUID=8584805045595806786L;

    protected String deviceId; //设备Id，移动设备就是IMEI
    protected String userId; //用户Id，若未登录，则用户Id为IMEI
    protected int PCDType; //设备类型；1=手机；2=设备；3=PC

    public String getDeviceId() {
        return deviceId;
    }
    public void setDeviceId(String deviceId) {
        this.deviceId=deviceId;
    }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId=userId;
    }

    public int getPCDType() {
        return PCDType;
    }
    public void setPCDType(int PCDType) {
        this.PCDType=PCDType;
    }

    /**
     * 是否是自制设备
     */
    public boolean isWTDevice() {
        return this.PCDType==2;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj==null) return false;
        try {
            return obj.hashCode()==this.hashCode();
        } catch(Exception e) {}
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    /**
     * 获得SessionId，SessionId就是UserId
     * @return
     */
    public String toString() {
        return this.deviceId+"::"+this.PCDType+"::"+this.userId;
    }
}