package com.woting.passport.UGA.service;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.spiritdata.framework.core.dao.mybatis.MybatisDAO;
import com.woting.passport.UGA.persis.pojo.MobileUsedPo;
import com.woting.push.user.PushUserUDKey;

/**
 * 移动设备用户使用情况
 * @author wh
 */
@Service
public class MobileUsedService {
    @Resource(name="defaultDAO")
    private MybatisDAO<MobileUsedPo> muDao;

    @PostConstruct
    public void initParam() {
        muDao.setNamespace("MOBILE_USED");
    }

    /**
     * 获取设备上的登录用户
     * @param imei 手机串号
     * @param PCDType 设备类型
     */
    public MobileUsedPo getUserUsedInDevice(String imei, int PCDType) {
        Map<String, String> paraM=new HashMap<String, String>();
        paraM.put("imei", imei);
        paraM.put("PCDType", PCDType+"");
        return muDao.getInfoObject("getUserUsedInDevice", paraM);
    }

    /**
     * 根据手机串号，获取最后使用情况
     * @param imei 手机串号
     * @param PCDType 设备类型
     */
    public MobileUsedPo getUsedInfo(PushUserUDKey pUdk) {
        Map<String, String> paraM=new HashMap<String, String>();
        paraM.put("imei", pUdk.getDeviceId());
        paraM.put("PCDType", pUdk.getPCDType()+"");
        paraM.put("userId", pUdk.getUserId());
        return muDao.getInfoObject("getUsedInfoByPUdk", paraM);
    }
}