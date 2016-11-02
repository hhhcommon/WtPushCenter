package com.woting.passport.UGA.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import com.spiritdata.framework.UGA.UgaUserService;
import com.spiritdata.framework.core.dao.mybatis.MybatisDAO;
import com.spiritdata.framework.util.StringUtils;
import com.woting.passport.UGA.persis.pojo.UserPo;

public class UserService implements UgaUserService {
    @Resource(name="defaultDAO")
    private MybatisDAO<UserPo> userDao;

    @PostConstruct
    public void initParam() {
        userDao.setNamespace("WT_USER");
    }

    @Override
    @SuppressWarnings("unchecked")
    public UserPo getUserByLoginName(String loginName) {
        try {
            return userDao.getInfoObject("getUserByLoginName", loginName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 根据绑定手机号，获得用户信息
     * @param userNum 用户号码
     * @return 用户信息
     */
    public UserPo getUserByPhoneNum(String phoneNum) {
        try {
            return userDao.getInfoObject("getUserByPhoneNum", phoneNum);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public UserPo getUserById(String userId) {
        try {
            return userDao.getInfoObject("getUserById", userId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<UserPo> getUserByIds(List<String> userIds) {
        try {
            String whereStr="";
            if (userIds!=null&&userIds.size()>0) {
                for (String id: userIds) {
                    whereStr+=" or id='"+id+"'";
                }
            }
            Map<String, String> param=new HashMap<String, String>();
            if (!StringUtils.isNullOrEmptyOrSpace(whereStr)) param.put("whereByClause", whereStr.substring(4));
            param.put("orderByClause", "cTime desc");
            return userDao.queryForList("getListByWhere", param);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获得组成员，为创建用户组使用
     * @param Members 组成员id，用逗号隔开
     * @return 组成员类表
     */
    public List<UserPo> getMembers4BuildGroup(String members) {
        try {
            List<UserPo> ul=userDao.queryForList("getMembers", members);
            return ul;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 根据用户号码获得用户信息
     * @param userNum 用户号码
     * @return 用户信息
     */
    public UserPo getUserByNum(String userNum) {
        return userDao.getInfoObject("getUserByNum", userNum);
    }
}