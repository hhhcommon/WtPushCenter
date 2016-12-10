package com.woting.audioSNS.sync;

import java.util.Map;

import com.woting.push.core.message.CompareMsg;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MsgNormal;

/**
 * 比较两个同步消息是否相同，注意：<br/>
 * 1-只有在同一用户下才有意义<br/>
 * 2-不比较消息Id，是通过消息的实际业务意义来进行比对的<br/>
 * @author wanghui
 */
public class CompareSyncMsg implements CompareMsg<Message> {
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
              if (_msg1.getMsgContent()!=null&&_msg2.getMsgContent()==null) return false;
              if (_msg1.getMsgContent()==null&&_msg2.getMsgContent()!=null) return false;
              if (_msg1.getMsgContent()!=null&&_msg2.getMsgContent()!=null) {
                  if ((_msg1.getMsgContent() instanceof Map)&&(_msg2.getMsgContent() instanceof Map)) {
                      try {
                          Map<?, ?> _m1=(Map<?, ?>)_msg1.getMsgContent();
                          Map<?, ?> _m2=(Map<?, ?>)_msg1.getMsgContent();
                          if (_m1.size()==_m2.size()) {
                              for (Object _key1:_m1.keySet()) {
                                  Object _v1=_m1.get(_key1);
                                  Object _v2=_m2.get(_key1);
                                  if (_v1!=null&&_v2!=null&&!_v1.equals(_v2)) return false;
                                  if ((_v1!=null&&_v2==null)||(_v1==null&&_v2!=null))  return false;
                              }
                              return true;
                          }
                          return false;
                      } catch(Exception e) {
                          return false;
                      }
                  } else _msg1.getMsgContent().equals(_msg2.getMsgContent());
              }
          }
          return false;
    }
}