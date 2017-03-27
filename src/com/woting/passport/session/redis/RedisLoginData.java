package com.woting.passport.session.redis;

/**
 * 用于Redis数据操作中，生成Key或相应的值。本接口仅为实现RedisSession框架中的数据获取。
 * RedisSession可被多平台实现
 * @author wanghui
 */
public interface RedisLoginData {
    /**
     * 得到用户登录的锁的key值——{Session_User_LoginLock=UserId=[]}
     * <p>其value是一随机数
     * @return 用户登录锁 key
     */
    public String getKey_Lock();

    /**
     * 得到用户设备登录状态的key值——{Session_User_Login=UserId_DType_DId=[]}
     * <p>其value是：最近访问时间=访问方法（方法描述字符串）
     * @return 用户设备登录状态的key值
     */
    public String getKey_UserLoginStatus();

    /**
     * 得到用户设备类型的key值——{Session_User_LoginDevice=UserId_DType=[]}
     * <p>其value是：用户用某类设备登录后，设备的ID
     * @return 用户设备类型 key
     */
    public String getKey_UserLoginDeviceType();

    /**
     * 得到设备类型对应的用户Id的key值，用于获得该设备的用户Id——{Session_DeviceLogin_UserId=DType_DId=[]}<br/>
     * 某具体设备当前登录的用户Id
     * <p>其value是：用户的ID
     * @return 设备类型对应的用户Id key
     */
    public String getKey_DeviceType_UserId();

    /**
     * 得到设备类型对应的用户Info的key值，用于获得该设备的用户Id——{Session_DeviceLogin_UserId=DType_DId=[]}<br/>
     * 某具体设备当前登录的用户的信息
     * <p>其value是：用户的信息，用Json串进行存储
     * @return 设备类型对应的用户Info key
     */
    public String getKey_DeviceType_UserInfo();
}