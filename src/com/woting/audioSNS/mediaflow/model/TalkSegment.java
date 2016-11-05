package com.woting.audioSNS.mediaflow.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.woting.passport.UGA.persis.pojo.UserPo;

/**
 * 一段对话
 * @author wanghui
 */
public class TalkSegment {
    public TalkSegment() {
        super();
        this.sendUserMap = new HashMap<String, UserPo>();
        this.sendFlagMap = new HashMap<String, Integer>();
        this.sendTimeMap = new HashMap<String, List<Long>>();
    }

    private WholeTalk wt; //本段话对应的完整对话
    private byte[] data; //本段对话的实际数据
    private long begin; //开始时间点：离通话开始时间
    private long end; //本包结束的时间点：离通话开始时间
    private int seqNum; //序列号：从0开始
    public Map<String, UserPo> sendUserMap; //需要传输的用户列表
    public Map<String/*userId*/, Integer/*状态：0未传送；1已传送；2传送成功；3传送失败(无须重传了)*/> sendFlagMap; //为各用户传送数据的结果情况
    public Map<String/*userId*/, List<Long>> sendTimeMap; //为各用户传送数据的结果情况

    public WholeTalk getWt() {
        return wt;
    }
    public void setWt(WholeTalk wt) {
        this.wt = wt;
    }
    public byte[] getData() {
        return data;
    }
    public void setData(byte[] data) {
        this.data = data;
    }
    public long getBegin() {
        return begin;
    }
    public void setBegin(long begin) {
        this.begin = begin;
    }
    public long getEnd() {
        return end;
    }
    public void setEnd(long end) {
        this.end = end;
    }
    public int getSeqNum() {
        return seqNum;
    }
    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    public Map<String, UserPo> getSendUserMap() {
        return sendUserMap;
    }
    public void setSendUserMap(Map<String, UserPo> sendUserMap) {
        this.sendUserMap=new HashMap<String, UserPo>();
        for (String k: sendUserMap.keySet()) {
            if (!k.equals(this.wt.getTalkerMk().toString())) {
                this.sendUserMap.put(k, sendUserMap.get(k));
                this.sendFlagMap.put(k, 0);
                this.sendTimeMap.put(k, new ArrayList<Long>());
            }
        }
    }
    public Map<String, Integer> getSendFlagMap() {
        return sendFlagMap;
    }
    public Map<String, List<Long>> getSendTimeMap() {
        return sendTimeMap;
    }

    public boolean sendOk() {
        for (String k: sendUserMap.keySet()) {
            if (this.sendFlagMap.get(k)<2) return false;
        }
        return true;
    }
}