package com.woting.push.core.monitor;

import com.woting.push.config.PushConfig;
import com.woting.push.core.mem.TcpGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.user.PushUserUDKey;

/**
 * 分发收到的纯净数据
 * @author wanghui
 */
public class DispatchMessage extends AbstractLoopMoniter<PushConfig> {
    private TcpGlobalMemory globlalMem=TcpGlobalMemory.getInstance();

    public DispatchMessage(PushConfig pc) {
        super(pc);
    }

    @Override
    public boolean initServer() {
        this.setLoopDelay(10);
        return true;
    }

    @Override
    public boolean canContinue() {
        return true;
    }

    @Override
    public void oneProcess() throws Exception {
        Message m=globlalMem.receiveMem.pollPureMsg();
        if (m!=null) {
            if (m.isAffirm()&&!(m instanceof MsgMedia)) {//处理回复消息
                PushUserUDKey mUdk=PushUserUDKey.buildFromMsg(m);
                globlalMem.sendMem.addMsg(mUdk, MessageUtils.buildAckMsg((MsgNormal)m));
            }
        }
        globlalMem.receiveMem.addTypeMsg(""+((MsgNormal)m).getBizType(), m);
    }

    @Override
    public void destroyServer() {
    }

}