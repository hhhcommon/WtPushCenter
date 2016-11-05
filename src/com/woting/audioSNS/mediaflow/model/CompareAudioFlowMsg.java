package com.woting.audioSNS.mediaflow.model;

import com.woting.push.core.message.CompareMsg;
import com.woting.push.core.message.MsgMedia;

public class CompareAudioFlowMsg implements CompareMsg<MsgMedia> {
    public boolean compare(MsgMedia msg1, MsgMedia msg2) {
        if (msg1.getFromType()==msg2.getFromType()
          &&msg1.getToType()==msg2.getToType()
          &&msg1.getBizType()==msg2.getBizType()) {
            if (msg1.getMediaData()==null&&msg2.getMediaData()==null) return true;
            if (((msg1.getMediaData()!=null&&msg2.getMediaData()!=null))
              &&msg1.getObjId()==msg2.getObjId()
              &&msg1.getTalkId()==msg2.getTalkId()
              &&msg1.getSeqNo()==msg2.getSeqNo()) return true;
        }
        return false;
    }
}