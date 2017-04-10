package com.woting.passport.UGA.model;

import java.util.ArrayList;
import java.util.List;

import com.spiritdata.framework.core.model.ModelSwapPo;
import com.spiritdata.framework.exceptionC.Plat0006CException;
import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.passport.UGA.persis.pojo.GroupPo;
import com.woting.passport.UGA.persis.pojo.UserPo;

/**
 * 带用户组成员的用户组信息
 * @author wanghui
 */
public class Group extends GroupPo implements ModelSwapPo {
    private static final long serialVersionUID=7365795273402631290L;

    private String innerPhoneNum;  //内部电话号吗
    private String defaultFreqNum;  //默认频段

    public String getDefaultFreqNum() {
        return defaultFreqNum;
    }
    public void setDefaultFreqNum(String defaultFreqNum) {
        this.defaultFreqNum=defaultFreqNum;
    }
    public String getInnerPhoneNum() {
        if (StringUtils.isNullOrEmptyOrSpace(this.innerPhoneNum)) return "3000";
        return innerPhoneNum;
    }
    public void setInnerPhoneNum(String innerPhoneNum) {
        this.innerPhoneNum=innerPhoneNum;
    }

    @Override
    public void buildFromPo(Object po) {
        if (po==null) throw new Plat0006CException("Po对象为空，无法从空对象得到概念/逻辑对象！");
        if (!(po instanceof GroupPo)) throw new Plat0006CException("Po对象不是GroupPo的实例，无法从此对象构建用户模型！");
        GroupPo _po=(GroupPo)po;
        this.setGroupId(_po.getGroupId());
        this.setGroupNum(_po.getGroupNum());
        this.setGroupName(_po.getGroupName());
        this.setGroupSignature(_po.getGroupSignature());
        this.setGroupPwd(_po.getGroupPwd());
        this.setGroupImg(_po.getGroupImg());
        this.setGroupType(_po.getGroupType());
        this.setPId(_po.getPId());
        this.setSort(_po.getSort());
        this.setCreateUserId(_po.getCreateUserId());
        this.setGroupMasterId(_po.getGroupMasterId());
        this.setAdminUserIds(_po.getAdminUserIds());
        this.setDescn(_po.getDescn());
        this.setCTime(_po.getCTime());
        this.setLmTime(_po.getLmTime());
    }
    @Override
    public Object convert2Po() {
        GroupPo ret=new GroupPo();
        if (StringUtils.isNullOrEmptyOrSpace(this.getGroupId())) ret.setGroupId(SequenceUUID.getUUIDSubSegment(4));
        else ret.setGroupId(this.getGroupId());
        ret.setGroupNum(this.getGroupNum());
        ret.setGroupName(this.getGroupName());
        ret.setGroupSignature(this.getGroupSignature());
        ret.setGroupPwd(this.getGroupPwd());
        ret.setGroupImg(this.getGroupImg());
        ret.setGroupType(this.getGroupType());
        ret.setPId(this.getPId());
        ret.setSort(this.getSort());
        ret.setCreateUserId(this.getCreateUserId());
        ret.setGroupMasterId(this.getGroupMasterId());
        ret.setAdminUserIds(this.getAdminUserIds());
        ret.setDescn(this.getDescn());
        ret.setCTime(this.getCTime());
        ret.setLmTime(this.getLmTime());
        return ret;
    }

    //组用户处理
    private List<UserPo> userList=new ArrayList<UserPo>();;
    public List<UserPo> getUserList() {
        return userList;
    }
    public void setUserList(List<UserPo> userList) {
        this.userList=userList;
    }
    public void addOneUser(UserPo up) {
        for (UserPo _up: userList) {
            if (up.getUserId().equals(_up.getUserId())) return;
        }
        userList.add(up);
    }
    public void delOneUser(String userId) {
        for (UserPo _up: userList) {
            if (userId.equals(_up.getUserId())) {
                userList.remove(_up);
                break;
            }
        }
    }

    /**
     * 获得此组用户
     * @param userId 用户Id
     * @return 用组中有用户id为userId的用户，返回他，否则返回空
     */
    public UserPo getUser(String userId) {
        UserPo up=null;
        for (UserPo _up: userList) {
            if (_up.getUserId().equals(userId)) {
                up=_up;
                break;
            }
        }
        return up;
    }
}