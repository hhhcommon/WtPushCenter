package com.woting.passport.UGA.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 根据用户组Id获得用户组信息，包括组下人员
     * @param groupId
     * @return
     */
    public Group getGroup(String groupId) {
        GroupPo gp=groupDao.getInfoObject("getGroupById", groupId);
        if (gp==null) return null;
        Group g=new Group();
        g.buildFromPo(gp);
        List<UserPo> upl=userDao.queryForList("getListUserInGroup", groupId);
        if (upl!=null&&!upl.isEmpty()) g.setUserList(upl);
        return g;
    }
    /**
     * 根据用户组Id获得用户组信息(不包括下级人员信息)
     * @param groupId
     * @return
     */
    public GroupPo getGroupPo(String groupId) {
        return groupDao.getInfoObject("getGroupById", groupId);
    }

    /**
     * 根据用户组Id获得用户组信息(不包括下级人员信息)
     * @param groupId
     * @return
     */
    public void fillGroupsAndUsers(Map<String, Group> gm, Map<String, UserPo> um) {
        Map<String, String> param=new HashMap<String, String>();
        param.put("orderByClause", "id");
        List<GroupPo> gl=groupDao.queryForList(param);

        if (gl!=null&&gl.size()>0) {
            Group item=null;
            for (GroupPo gp: gl) {
                item=new Group();
                item.buildFromPo(gp);
                gm.put(gp.getGroupId(), item);
            }
            List<Map<String, Object>> ul=userDao.queryForListAutoTranform("getAllUserInGroup", null);
            if (ul!=null&&ul.size()>0) {
                int i=0;
                UserPo up=null;
                Map<String, Object> ui=null;
                String addGroupId="";
                List<UserPo> userList=null;
                while (i<ul.size()) {
                    ui=ul.get(i++);
                    up=um.get((String)ui.get("id"));
                    if (up==null) {
                        up=new UserPo(ui);
                        um.put(up.getUserId(), up);
                    }
                    if (!addGroupId.equals((String)ui.get("groupId"))) {
                        if (userList!=null&&gm.get(addGroupId)!=null) {
                            gm.get(addGroupId).setUserList(userList);
                        }
                        addGroupId=(String)ui.get("groupId");
                        userList=new ArrayList<UserPo>();
                    }
                    userList.add(up);
                }
                //处理最后一个
                if (userList!=null&&gm.get(addGroupId)!=null) {
                    gm.get(addGroupId).setUserList(userList);
                }
            }
        }
    }
}