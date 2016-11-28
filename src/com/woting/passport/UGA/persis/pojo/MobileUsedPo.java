package com.woting.passport.UGA.persis.pojo;

import java.sql.Timestamp;
import com.spiritdata.framework.core.model.BaseObject;

/**
 * 用户使用
 * @author wh
 */
public class MobileUsedPo extends BaseObject {
    private static final long serialVersionUID=6647191780195842477L;

    private String muId; //用户使用ID
    private String imei; //手机串号
    private int PCDType; //手机串号
    private String userId; //用户ID
    private int status; //状态：1-登录；2-注销；
    private Timestamp lmTime; //最后修改时间:last modify time

    public String getMuId() {
        return muId;
    }
    public void setMuId(String muId) {
        this.muId=muId;
    }
    public String getImei() {
        return imei;
    }
    public void setImei(String imei) {
        this.imei=imei;
    }
    public int getPCDType() {
        return PCDType;
    }
    public void setPCDType(int PCDType) {
        this.PCDType=PCDType;
    }
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId=userId;
    }
    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status=status;
    }
    public Timestamp getLmTime() {
        return lmTime;
    }
    public void setLmTime(Timestamp lmTime) {
        this.lmTime=lmTime;
    }
}