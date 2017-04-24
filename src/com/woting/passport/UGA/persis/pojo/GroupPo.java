package com.woting.passport.UGA.persis.pojo;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import com.spiritdata.framework.core.model.BaseObject;
import com.spiritdata.framework.util.StringUtils;

public class GroupPo extends BaseObject {
    private static final long serialVersionUID=-4171166651180143388L;

    private String groupId; //用户组id
    private String groupNum; //组号，用于公开的号码
    private String groupName; //用户组名称
    private String groupSignature; //用户组签名，只有管理员可以修改
    private String groupPwd; //用户组密码，可为空
    private String groupImg; //用户组头像
    private int groupType; //验证群0；公开群1[原来的号码群]；密码群2
    private String pId; //上级用户组Id
    private int sort; //用户组排序0
    private String createUserId; //创建者id
    private String groupMasterId; //群主id
    private String adminUserIds;  //管理者id，可以有多个管理者，第一个为主管理者
    private String defaultFreq; //用户组默认频率如：“456.3477-457.3455, 466.222-469.223, 488.22, 456.dssdf”,注意每一项用逗号隔开，每一项可以是单频也可以是双频，若是双频，用短线隔开，且前面的频率要小于后面的频率
    private String descn; //用户描述
    private Timestamp CTime; //记录创建时间
    private Timestamp lmTime; //最后修改时间:last modify time
    private int groupCount; //组用户个数
    private String groupAlias; //某用户对用户所定别名，仅在用户查组时有效
    private String groupDescn; //某用户对用户组的描述，仅在用户查组时有效

    public String getGroupId() {
        return groupId;
    }
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    public String getGroupNum() {
        return groupNum;
    }
    public void setGroupNum(String groupNum) {
        this.groupNum = groupNum;
    }
    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    public String getGroupSignature() {
        return groupSignature;
    }
    public void setGroupSignature(String groupSignature) {
        this.groupSignature = groupSignature;
    }
    public String getGroupPwd() {
        return groupPwd;
    }
    public void setGroupPwd(String groupPwd) {
        this.groupPwd = groupPwd;
    }
    public String getGroupImg() {
        return groupImg;
    }
    public void setGroupImg(String groupImg) {
        this.groupImg = groupImg;
    }
    public int getGroupType() {
        return groupType;
    }
    public void setGroupType(int groupType) {
        this.groupType = groupType;
    }
    public String getPId() {
        return pId;
    }
    public void setPId(String pId) {
        this.pId = pId;
    }
    public int getSort() {
        return sort;
    }
    public void setSort(int sort) {
        this.sort = sort;
    }
    public String getCreateUserId() {
        return createUserId;
    }
    public void setCreateUserId(String createUserId) {
        this.createUserId = createUserId;
    }
    public String getAdminUserIds() {
        return adminUserIds;
    }
    public void setAdminUserIds(String adminUserIds) {
        this.adminUserIds = adminUserIds;
    }
    public String getDefaultFreq() {
        return defaultFreq;
    }
    public void setDefaultFreq(String defaultFreq) {
        this.defaultFreq = defaultFreq;
    }
    public String getDescn() {
        return descn;
    }
    public void setDescn(String descn) {
        this.descn = descn;
    }
    public Timestamp getCTime() {
        return CTime;
    }
    public void setCTime(Timestamp cTime) {
        CTime = cTime;
    }
    public Timestamp getLmTime() {
        return lmTime;
    }
    public void setLmTime(Timestamp lmTime) {
        this.lmTime = lmTime;
    }
    public int getGroupCount() {
        return groupCount;
    }
    public void setGroupCount(int groupCount) {
        this.groupCount = groupCount;
    }
    public String getGroupMasterId() {
        return groupMasterId;
    }
    public void setGroupMasterId(String groupMasterId) {
        this.groupMasterId = groupMasterId;
    }
    public String getGroupAlias() {
        return groupAlias;
    }
    public void setGroupAlias(String groupAlias) {
        this.groupAlias = groupAlias;
    }
    public String getGroupDescn() {
        return groupDescn;
    }
    public void setGroupDescn(String groupDescn) {
        this.groupDescn = groupDescn;
    }

    public Map<String, Object> toHashMap4View() {
        Map<String, Object> retM=new HashMap<String, Object>();

        retM.put("GroupId", this.groupId);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupNum)) retM.put("GroupNum", this.groupNum);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupName)) retM.put("GroupName", this.groupName);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupSignature)) retM.put("GroupSignature", this.groupSignature);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupImg)) retM.put("GroupImg", this.groupImg);
        retM.put("GroupType", this.groupType);
        if (!StringUtils.isNullOrEmptyOrSpace(this.createUserId)) retM.put("GroupCreator", this.createUserId);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupMasterId)) retM.put("GroupMasterId", this.groupMasterId);
        if (!StringUtils.isNullOrEmptyOrSpace(this.adminUserIds)) retM.put("GroupManager", this.adminUserIds);
        if (!StringUtils.isNullOrEmptyOrSpace(this.defaultFreq)) retM.put("GroupFreq", this.defaultFreq);
        if (!StringUtils.isNullOrEmptyOrSpace(this.descn)) retM.put("GroupOriDescn", this.descn);
        retM.put("GroupCount", this.groupCount);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupAlias)) retM.put("GroupMyAlias", this.groupAlias);
        if (!StringUtils.isNullOrEmptyOrSpace(this.groupDescn)) retM.put("GroupMyDescn", this.groupDescn);

        return retM;
    }
}