package com.woting.passport.UGA.persis.pojo;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import com.spiritdata.framework.UGA.UgaUser;
import com.spiritdata.framework.util.StringUtils;

public class UserPo extends UgaUser {
    private static final long serialVersionUID=400373609903981461L;

    private String nickName; //昵称
    private String userNum; //用户号，用于公开的号码
    private String userSign; //用户签名
    private Timestamp birthday; //生日
    private String starSign; //星座
    private String mainPhoneNum; //用户主手机号码，用户可能有多个手机号码
    private int phoneNumIsPub; //是否允许手机号码搜索或公布手机号码:0不公开；1公开
    private String mailAddress; //用户邮箱
    private int userType; //用户分类：对应OwnerType，1xx::系统:100-我们自己的系统(cm/crawl/push等);101-其他系统(wt_Organize表中的Id);2xx::用户:200-后台系统用户;201-前端用户-wt_Member表中的用户Id
    private int userClass; //用户类型，现在还没有用，比如是一般用户还是管理原等
    private int userState;//用户状态，0-2,0代表未激活的用户，1代表已激用户，2代表失效用户,3根据邮箱找密码的用户
    private String portraitBig;//用户头像大
    private String portraitMini;//用户头像小
    private String homepage; //用户主页
    private String descn; //用户描述
    private Timestamp CTime; //记录创建时间
    private Timestamp lmTime; //最后修改时间:last modify time

    public String getUserNum() {
        return userNum;
    }
    public void setUserNum(String userNum) {
        this.userNum = userNum;
    }
    public String getMainPhoneNum() {
        return mainPhoneNum;
    }
    public void setMainPhoneNum(String mainPhoneNum) {
        this.mainPhoneNum = mainPhoneNum;
    }
    public String getMailAddress() {
        return mailAddress;
    }
    public void setMailAddress(String mailAddress) {
        this.mailAddress = mailAddress;
    }
    public int getUserType() {
        return userType;
    }
    public void setUserType(int userType) {
        this.userType = userType;
    }
    public int getUserClass() {
        return userClass;
    }
    public void setUserClass(int userClass) {
        this.userClass = userClass;
    }
    public int getUserState() {
        return userState;
    }
    public void setUserState(int userState) {
        this.userState = userState;
    }
    public String getPortraitBig() {
        return portraitBig;
    }
    public void setPortraitBig(String portraitBig) {
        this.portraitBig = portraitBig;
    }
    public String getPortraitMini() {
        return portraitMini;
    }
    public void setPortraitMini(String portraitMini) {
        this.portraitMini = portraitMini;
    }
    public String getDescn() {
        return descn;
    }
    public void setDescn(String descn) {
        this.descn = descn;
    }
    public String getHomepage() {
        return homepage;
    }
    public void setHomepage(String homepage) {
        this.homepage = homepage;
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
    public String getNickName() {
        return nickName;
    }
    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
    public String getUserSign() {
        return userSign;
    }
    public void setUserSign(String userSign) {
        this.userSign = userSign;
    }
    public Timestamp getBirthday() {
        return birthday;
    }
    public void setBirthday(Timestamp birthday) {
        this.birthday = birthday;
    }
    public String getStarSign() {
        return starSign;
    }
    public void setStarSign(String starSign) {
        this.starSign = starSign;
    }
    public void setPhoneNumIsPub(boolean isPub) {
        this.phoneNumIsPub=isPub?1:0;
    }
    public void setPhoneNumIsPub(int phoneNumIsPub) {
        this.phoneNumIsPub = phoneNumIsPub;
    }
    public boolean isPubPhoneNum() {
        return phoneNumIsPub==1;
    }
    public int getPhoneNumIsPub() {
        return phoneNumIsPub;
    }

    public UserPo(Map<String, Object> um) {
        super();
        fromHashMap(um);
        if (!StringUtils.isNullOrEmptyOrSpace((String)um.get("id"))) setUserId((String)um.get("id"));
    }

    public Map<String, Object> toHashMap4View() {
        Map<String, Object> retM = new HashMap<String, Object>();
        if (!StringUtils.isNullOrEmptyOrSpace(this.userId)) retM.put("UserId", this.userId);
        if (!StringUtils.isNullOrEmptyOrSpace(this.userName)) retM.put("RealName", this.userName);
        if (!StringUtils.isNullOrEmptyOrSpace(this.userNum)) retM.put("UserNum", this.userNum);
        if (!StringUtils.isNullOrEmptyOrSpace(this.userSign)) retM.put("UserSign", this.userSign);
        if (!StringUtils.isNullOrEmptyOrSpace(this.loginName)) retM.put("UserName", this.loginName);
        if (!StringUtils.isNullOrEmptyOrSpace(this.nickName)) retM.put("NickName", this.nickName);
        if (!StringUtils.isNullOrEmptyOrSpace(this.mainPhoneNum)&&this.isPubPhoneNum()) retM.put("PhoneNum", this.mainPhoneNum);
        if (!StringUtils.isNullOrEmptyOrSpace(this.descn)) retM.put("Descn", this.descn);
        if (!StringUtils.isNullOrEmptyOrSpace(this.portraitBig)) retM.put("PortraitBig", this.portraitBig);
        if (!StringUtils.isNullOrEmptyOrSpace(this.portraitMini)) retM.put("PortraitMini", this.portraitMini);
        return retM;
    }
}