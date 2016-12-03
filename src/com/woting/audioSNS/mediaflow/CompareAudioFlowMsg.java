package com.woting.audioSNS.mediaflow;

import com.woting.push.core.message.CompareMsg;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgMedia;

public class CompareAudioFlowMsg implements CompareMsg<Message> {
    /**
     * 
     */
    public boolean compare(Message msg1, Message msg2) {
        if (!(msg1 instanceof MsgMedia)) return false;
        if (!(msg2 instanceof MsgMedia)) return false;
        MsgMedia _msg1=(MsgMedia)msg1;
        MsgMedia _msg2=(MsgMedia)msg2;
        if (_msg1.getFromType()==_msg2.getFromType()
          &&_msg1.getToType()==_msg2.getToType()
          &&_msg1.getBizType()==_msg2.getBizType()) {
            if (_msg1.getMediaData()==null&&_msg2.getMediaData()==null) return true;
            if (((_msg1.getMediaData()!=null&&_msg2.getMediaData()!=null))
              &&_msg1.getObjId()==_msg2.getObjId()
              &&_msg1.getTalkId()==_msg2.getTalkId()
              &&_msg1.getSeqNo()==_msg2.getSeqNo()) return true;
        }
        return false;
    }
}