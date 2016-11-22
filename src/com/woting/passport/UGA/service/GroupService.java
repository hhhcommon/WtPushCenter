package com.woting.passport.UGA.service;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.spiritdata.framework.core.dao.mybatis.MybatisDAO;
import com.woting.passport.UGA.model.Group;
import com.woting.passport.UGA.persis.pojo.GroupPo;
import com.woting.passport.UGA.persis.pojo.UserPo;

@Service
public class GroupService {
    @Resource(name="defaultDAO")
    private MybatisDAO<GroupPo> groupDao;
    @Resource(name="defaultDAO")
    private MybatisDAO<UserPo> userDao;

    @PostConstruct
    public void initParam() {
        userDao.setNamespace("WT_USER");
        groupDao.setNamespace("WT_GROUP");
    }
    /**
     * 根据用户组Id获得用户组信息
     * @param groupId
     * @return
     */
    public Group getGroup(String groupId) {
        GroupPo gp=groupDao.getInfoObject("getGroupById", groupId);
        Group g=new Group();
        g.buildFromPo(gp);
        List<UserPo> upl=userDao.queryForList("getListUserInGroup", groupId);
        if (upl!=null&&!upl.isEmpty()) g.setUserList(upl);
        return g;
    }
}