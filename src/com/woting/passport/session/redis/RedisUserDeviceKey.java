package com.woting.passport.session.redis;

import com.spiritdata.framework.exceptionC.Plat5101CException;
import com.spiritdata.framework.util.StringUtils;
import com.woting.passport.session.key.UserDeviceKey;

public class RedisUserDeviceKey extends UserDeviceKey implements RedisLoginData {
    private static final long serialVersionUID = 2017041361668506482L;

    public RedisUserDeviceKey(UserDeviceKey udKey) {
        if (udKey==null) throw new Plat5101CException("用户设备Key不能为空");
        if (StringUtils.isNullOrEmptyOrSpace(this.getDeviceId()))  new Plat5101CException("未设置设备Id");
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");

        this.setUserId(udKey.getUserId());
        this.setPCDType(udKey.getPCDType());
        this.setDeviceId(udKey.getDeviceId());
    }

    @Override
    public String getKey_Lock() {
        String ret="Session_User_LoginLock::UserId::";
        if (!StringUtils.isNullOrEmptyOrSpace(this.getUserId())) return ret+this.getUserId();
        else
        if (!StringUtils.isNullOrEmptyOrSpace(this.getDeviceId())) return ret+this.getDeviceId();
        throw new Plat5101CException("未设置设备Id");
    }

    @Override
    public String getKey_UserLoginStatus() {
        if (StringUtils.isNullOrEmptyOrSpace(this.getDeviceId()))  new Plat5101CException("未设置设备Id");
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");

        String _userId=StringUtils.isNullOrEmptyOrSpace(this.getUserId())?this.getDeviceId():this.getUserId();
        return "Session_User_Login::UserId_DType_DId::"+_userId+"_"+this.getPCDType()+"_"+this.getDeviceId();
    }

    @Override
    public String getKey_UserLoginDeviceType() {
        if (StringUtils.isNullOrEmptyOrSpace(this.getDeviceId()))  new Plat5101CException("未设置设备Id");
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");

        String _userId=StringUtils.isNullOrEmptyOrSpace(this.getUserId())?this.getDeviceId():this.getUserId();
        return "Session_User_Login::UserId_DType::"+_userId+"_"+this.getPCDType();
    }
    public String getKey_UserLoginDeviceType_OnlyUseUserId() {
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");
        if (StringUtils.isNullOrEmptyOrSpace(this.getUserId()))  new Plat5101CException("未设置用户Id");

        return "Session_User_Login::UserId_DType::"+this.getUserId()+"_"+this.getPCDType();
    }

    @Override
    public String getKey_DeviceType_UserId() {
        if (StringUtils.isNullOrEmptyOrSpace(this.getDeviceId()))  new Plat5101CException("未设置设备Id");
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");

        return "Session_DeviceLogin_UserId::DType_DId::"+this.getPCDType()+"_"+this.getDeviceId();
    }

    @Override
    public String getKey_DeviceType_UserInfo() {
        if (StringUtils.isNullOrEmptyOrSpace(this.getDeviceId()))  new Plat5101CException("未设置设备Id");
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");

        return "Session_DeviceLogin_UserInfo::DType_DId::"+this.getPCDType()+"_"+this.getDeviceId();
    }

    public String getKey_UserPhoneCheck() {
        if (StringUtils.isNullOrEmptyOrSpace(this.getDeviceId()))  new Plat5101CException("未设置设备Id");
        if (this.getPCDType()<=0)  new Plat5101CException("未设置PCDType");

        String _userId=StringUtils.isNullOrEmptyOrSpace(this.getUserId())?this.getDeviceId():this.getUserId();
        return "User_PhoneCheck::UserId_DType_DId::"+_userId+"_"+this.getPCDType()+"_"+this.getDeviceId();
    }
}