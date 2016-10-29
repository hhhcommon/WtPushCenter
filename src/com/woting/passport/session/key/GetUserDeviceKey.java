package com.woting.passport.session.key;

/**
 * 获得用户设备登录信息的接口<br/>
 * 继承此接口的类应该是从前端获取参数的类，从这些参数再次获取用户设备登录信息
 * @author wanghui
 */
public interface GetUserDeviceKey {
     /**
      * 获得用户设备Key，
      * 注意，返回的设备Key必须有设备类型分类和设备Id
      * @return 
      */
     public UserDeviceKey getUserDeviceKey();
}