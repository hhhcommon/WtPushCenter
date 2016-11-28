package com.woting.audioSNS.intercom;

import com.woting.push.core.message.CompareMsg;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;

public class CompareGroupMsg implements CompareMsg<MsgNormal> {

    @Override
    public boolean compare(MsgNormal msg1, MsgNormal msg2) {
        if (msg1.getFromType()==msg2.getFromType()
          &&msg1.getToType()==msg2.getToType()
          &&msg1.getBizType()==msg2.getBizType()
          &&msg1.getCmdType()==msg2.getCmdType()
          &&msg1.getCommand()==msg2.getCommand() ) {
            if (msg1.getMsgContent()==null&&msg2.getMsgContent()==null) return true;
            if (((msg1.getMsgContent()!=null&&msg2.getMsgContent()!=null))
              &&(((MapContent)msg1.getMsgContent()).get("GroupId").equals(((MapContent)msg2.getMsgContent()).get("GroupId")))) return true;
        }
        return false;
    }
}