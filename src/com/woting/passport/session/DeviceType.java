package com.woting.passport.session;

public enum DeviceType {
    MOBILE(1, "手机"),
    WOTING(2, "我听设备"),
    PC(3, "PC客户端"),
    ERR(-1, "错误客户端");

    private int _value;
    private String _name;
    private DeviceType(int value, String name) {
        _value=value;
        _name=name;
    }

    public static DeviceType buildDtByPCDType(int pcdType) {
        if (pcdType==1) return MOBILE;
        else if (pcdType==2) return WOTING;
        else if (pcdType==3) return PC;
        else return ERR;
    }

    public int getPCDType() {
        return _value;
    }

    public String getName() {
        return _name;
    }
}