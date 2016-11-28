package com.woting.passport.UGA.persis.pojo;

import java.sql.Timestamp;
import com.spiritdata.framework.core.model.BaseObject;

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
    private String adminUserIds;  //管理者id，可以有多个管理者，第一个为主管理者
    private String descn; //用户描述
    private Timestamp CTime; //记录创建时间
    private Timestamp lmTime; //最后修改时间:last modify time
    private int groupCount; //组用户个数

    public String getGroupId() {
        return groupId;
    }
    public void setGroupId(String groupId) {
        this.groupId=groupId;
    }
    public String getGroupNum() {
        return groupNum;
    }
    public void setGroupNum(String groupNum) {
        this.groupNum=groupNum;
    }
    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName=groupName;
    }
    public String getGroupSignature() {
        return groupSignature;
    }
    public void setGroupSignature(String groupSignature) {
        this.groupSignature=groupSignature;
    }
    public String getGroupPwd() {
        return groupPwd;
    }
    public void setGroupPwd(String groupPwd) {
        this.groupPwd=groupPwd;
    }
    public String getGroupImg() {
        return groupImg;
    }
    public void setGroupImg(String groupImg) {
        this.groupImg=groupImg;
    }
    public int getGroupType() {
        return groupType;
    }
    public void setGroupType(int groupType) {
        this.groupType=groupType;
    }
    public String getPId() {
        return pId;
    }
    public void setPId(String pId) {
        this.pId=pId;
    }
    public int getSort() {
        return sort;
    }
    public void setSort(int sort) {
        this.sort=sort;
    }
    public String getCreateUserId() {
        return createUserId;
    }
    public void setCreateUserId(String createUserId) {
        this.createUserId=createUserId;
    }
    public String getAdminUserIds() {
        return adminUserIds;
    }
    public void setAdminUserIds(String adminUserIds) {
        this.adminUserIds=adminUserIds;
    }
    public String getDescn() {
        return descn;
    }
    public void setDescn(String descn) {
        this.descn=descn;
    }
    public Timestamp getCTime() {
        return CTime;
    }
    public void setCTime(Timestamp cTime) {
        CTime=cTime;
    }
    public Timestamp getLmTime() {
        return lmTime;
    }
    public void setLmTime(Timestamp lmTime) {
        this.lmTime=lmTime;
    }
    public int getGroupCount() {
        return groupCount;
    }
    public void setGroupCount(int groupCount) {
        this.groupCount=groupCount;
    }
}