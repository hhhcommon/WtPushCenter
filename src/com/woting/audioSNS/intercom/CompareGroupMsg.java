package com.woting.audioSNS.intercom;

import com.woting.push.core.message.CompareMsg;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;

public class CompareGroupMsg implements CompareMsg<Message> {

    @Override
    public boolean compare(Message msg1, Message msg2) {
        if (!(msg1 instanceof MsgNormal)) return false;
        if (!(msg2 instanceof MsgNormal)) return false;
        MsgNormal _msg1=(MsgNormal)msg1;
        MsgNormal _msg2=(MsgNormal)msg2;
        if (_msg1.getFromType()==_msg2.getFromType()
          &&_msg1.getToType()==_msg2.getToType()
          &&_msg1.getBizType()==_msg2.getBizType()
          &&_msg1.getCmdType()==_msg2.getCmdType()
          &&_msg1.getCommand()==_msg2.getCommand() ) {
            if (_msg1.getMsgContent()==null&&_msg2.getMsgContent()==null) return true;
            if (((_msg1.getMsgContent()!=null&&_msg2.getMsgContent()!=null))
              &&(((MapContent)_msg1.getMsgContent()).get("GroupId").equals(((MapContent)_msg2.getMsgContent()).get("GroupId")))) return true;
        }
        return false;
    }
}