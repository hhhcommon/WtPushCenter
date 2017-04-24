package com.woting.push.core.monitor.socket.netty;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spiritdata.framework.core.cache.CacheEle;
import com.spiritdata.framework.core.cache.SystemCache;
import com.spiritdata.framework.util.DateUtils;
import com.spiritdata.framework.util.JsonUtils;
import com.spiritdata.framework.util.SequenceUUID;
import com.spiritdata.framework.util.StringUtils;
import com.woting.audioSNS.calling.mem.CallingMemory;
import com.woting.audioSNS.intercom.mem.IntercomMemory;
import com.woting.audioSNS.mediaflow.MediaConfig;
import com.woting.audioSNS.mediaflow.mem.TalkMemory;
import com.woting.audioSNS.mediaflow.monitor.DealMediaMsg;
import com.woting.push.PushConstants;
import com.woting.push.config.PushConfig;
import com.woting.push.core.SocketHandleConfig;
import com.woting.push.core.mem.PushGlobalMemory;
import com.woting.push.core.message.Message;
import com.woting.push.core.message.MessageUtils;
import com.woting.push.core.message.MsgMedia;
import com.woting.push.core.message.MsgNormal;
import com.woting.push.core.message.content.MapContent;
import com.woting.push.core.monitor.socket.netty.sendthread.ResendNeedCtrAffirmMsg;
import com.woting.push.core.monitor.socket.netty.sendthread.SendAllMsg;
import com.woting.push.core.service.SessionService;
import com.woting.push.ext.SpringShell;
import com.woting.push.user.PushUserUDKey;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

public class NettyHandler extends ChannelInboundHandlerAdapter {
    private Logger logger=LoggerFactory.getLogger(NettyHandler.class);

    private SessionService sessionService=(SessionService)SpringShell.getBean("sessionService");
    @SuppressWarnings("unchecked")
    private PushConfig pConf=((CacheEle<PushConfig>)SystemCache.getCache(PushConstants.PUSH_CONF)).getContent();

    private PushGlobalMemory globalMem=PushGlobalMemory.getInstance();
    private IntercomMemory intercomMem=IntercomMemory.getInstance();
    private CallingMemory callingMem=CallingMemory.getInstance();
    private TalkMemory talkMem=TalkMemory.getInstance();

    private long lastVisitTime=System.currentTimeMillis();
    @SuppressWarnings("unchecked")
    private SocketHandleConfig sc=((CacheEle<SocketHandleConfig>)SystemCache.getCache(PushConstants.SOCKETHANDLE_CONF)).getContent();

    public static final AttributeKey<PushUserUDKey> CHANNEL_PUDKEY=AttributeKey.valueOf("ChannelPudKey"); //用户设备绑定
    public static final AttributeKey<String> CHANNEL_CURNMID=AttributeKey.valueOf("CurrentNotifyMsgId"); //当前需要发送的通知消息Id
    public static final AttributeKey<String> CHANNEL_DEVKEY=AttributeKey.valueOf("ChannelDevKey"); //仅设备绑定

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof byte[]) {
            byte[] _data=(byte[])msg;
            if (MessageUtils.decideBeat(_data)==2) {
                lastVisitTime=System.currentTimeMillis();
                ctx.writeAndFlush(MessageUtils.BEAT_SERVER);//回写心跳
                logger.debug("{}[{}]_收到心跳:{}", ctx.toString(), lastVisitTime, new String(_data));
            } else { 
                logger.info("{}[{}]_收到错误数据:{}", ctx.toString(), lastVisitTime, new String((byte[])msg));
            }
        } else {
            if (msg instanceof Message) {
                lastVisitTime=System.currentTimeMillis();
                logger.info("{}[{}]_收到消息:{}", ctx.toString(), lastVisitTime, JsonUtils.objToJson(msg));
                if (msg instanceof MsgMedia) dealMDAMsg(ctx, (MsgMedia)msg);
                else
                if (msg instanceof MsgNormal) {
                    MsgNormal mn=(MsgNormal)msg;
                    PushUserUDKey _pUdk=PushUserUDKey.buildFromMsg(mn);
                    if (_pUdk!=null) {
                        if (mn.getBizType()==15) {//注册消息
                            if (!mn.isAck()) {
                                dealRegisterCTLMsg(ctx, _pUdk, mn);
                                new SendAllMsg(
                                    (PushConfig)SystemCache.getCache(PushConstants.PUSH_CONF).getContent(),
                                    (MediaConfig)SystemCache.getCache(PushConstants.MEDIA_CONF).getContent(),
                                    ctx
                                ).start();
                            }
                        } else dealNormalCTLMsg(ctx, _pUdk, mn);//处理一般消息
                    }
                }
            }
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        logger.info("频道注销::"+ctx.toString());
        unBind(ctx);
    }

    @Override  
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.info(cause.getMessage());
        ctx.disconnect();
        ctx.channel().close();
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent)evt).state()==IdleState.READER_IDLE) {//处理心跳
                dealHealth(ctx);
            }
            if (((IdleStateEvent)evt).state()==IdleState.WRITER_IDLE) {//处理控制消息重发
                new ResendNeedCtrAffirmMsg(ctx).start();
            }
        }
    }

    /*
     * 处理健康过程
     */
    private void dealHealth(ChannelHandlerContext ctx) {
        long _curTime=System.currentTimeMillis();
        long _period=_curTime-lastVisitTime;
        if (_period>sc.get_ExpireTime()) {
            logger.debug("超时关闭:最后访问时间["
                        +DateUtils.convert2LocalStr("yyyy-MM-dd HH:mm:ss:SSS", new Date(lastVisitTime))+"("+lastVisitTime+")],当前时间["
                        +DateUtils.convert2LocalStr("yyyy-MM-dd HH:mm:ss:SSS", new Date(_curTime))+"("+_curTime+")],过期时间["
                        +sc.get_ExpireTime()+"]，超时时长["+_period+"]");
            ctx.close();
        }
    }
    /*
     * 取消绑定并关闭
     */
    private void unBind(ChannelHandlerContext ctx) {
        //退出系统前，先把绑定关系清除掉。
        PushUserUDKey thisPushUdk=(ctx.channel().attr(CHANNEL_PUDKEY)==null?null:(ctx.channel().attr(CHANNEL_PUDKEY).get())==null?null:ctx.channel().attr(CHANNEL_PUDKEY).get());
        globalMem.unbindPushUserANDSocket(thisPushUdk, ctx);

        String dkey=ctx.channel().attr(CHANNEL_DEVKEY)==null?null:(ctx.channel().attr(CHANNEL_DEVKEY).get()==null?null:ctx.channel().attr(CHANNEL_DEVKEY).get());
        if (!StringUtils.isNullOrEmptyOrSpace(dkey)) {
            PushUserUDKey _tpUdk=new PushUserUDKey();
            String[] sp=dkey.split("=");
            _tpUdk.setDeviceId(sp[0]);
            _tpUdk.setPCDType(Integer.parseInt(sp[1]));
            globalMem.unbindDeviceANDsocket(_tpUdk, ctx);
        }
    }

    /*
     * 处理音视频消息
     */
    private void dealMDAMsg(ChannelHandlerContext ctx, MsgMedia mm) throws InterruptedException {
        Attribute<PushUserUDKey> c_PUDK=ctx.channel().attr(CHANNEL_PUDKEY);
        boolean isBind=false;
        if (c_PUDK.get()!=null) {
            isBind=true;
            mm.setExtInfo(c_PUDK.get());
            //globalMem.receiveMem.putTypeMsg("media", _msg);
            new DealMediaMsg((mm.getMediaType()==1?"音频":"视频")
                +(mm.isAck()?"回执":"数据")+"包处理：[通话类型="+(mm.getBizType()==1?"对讲":"电话")
                +"；talkId="+mm.getTalkId()+"；seqNo="+mm.getSeqNo()+"；]"
                ,mm, globalMem, intercomMem, callingMem, talkMem, sessionService).start();
        }
        if (mm.isCtlAffirm()&&!isBind) {
            MsgMedia retMm=new MsgMedia();
            retMm.setFromType(1);
            retMm.setToType(0);
            retMm.setMsgType(0);
            retMm.setAffirm(0);
            retMm.setBizType(mm.getBizType());
            retMm.setTalkId(mm.getTalkId());
            retMm.setChannelId(mm.getChannelId());
            retMm.setSeqNo(mm.getSeqNo());
            retMm.setSendTime(System.currentTimeMillis());
            retMm.setReturnType(0);//未绑定用户，不能处理这样的媒体包
            ctx.writeAndFlush(retMm);
        }
    }
    /*
     * 处理非注册消息
     */
    private void dealNormalCTLMsg(ChannelHandlerContext ctx, PushUserUDKey pUdk, MsgNormal mn) throws InterruptedException {
        Attribute<PushUserUDKey> c_PUDK=ctx.channel().attr(CHANNEL_PUDKEY);
        if (mn.isAck()) {//若是应答消息，这个先不做处理
            globalMem.receiveMem.putTypeMsg(""+((MsgNormal)mn).getBizType(), mn);
        } else {
            if (pUdk.equals(c_PUDK.get())) {
                if (mn.isCtlAffirm()) { //若需要控制回复
                    MsgNormal ctlAffirmMsg=MessageUtils.buildAckMsg((MsgNormal)mn);
                    ctlAffirmMsg.setUserId(pConf.get_ServerType());
                    ctlAffirmMsg.setDeviceId(pConf.get_ServerName());
                    ctx.writeAndFlush(ctlAffirmMsg);
                }
                globalMem.receiveMem.putTypeMsg(""+((MsgNormal)mn).getBizType(), mn);
            } else { //消息中的用户信息与当前绑定的用户不同，回复未登录消息
                if (mn.isCtlAffirm()||mn.isBizAffirm()) { //需要回复消息
                    MsgNormal noLogMsg=MessageUtils.buildAckMsg((MsgNormal)mn);
                    noLogMsg.setCmdType(1);
                    noLogMsg.setUserId(pConf.get_ServerType());
                    noLogMsg.setDeviceId(pConf.get_ServerName());
                    ctx.writeAndFlush(noLogMsg);
                }
            }
        }
    }
    /*
     * 处理注册消息
     */
    private void dealRegisterCTLMsg(ChannelHandlerContext ctx, PushUserUDKey pUdk, MsgNormal mn) throws InterruptedException {
        Attribute<PushUserUDKey> c_PUDK=ctx.channel().attr(CHANNEL_PUDKEY);
        Attribute<String> c_DevK=ctx.channel().attr(CHANNEL_DEVKEY);

        boolean bindDeviceFlag=globalMem.bindDeviceANDsocket(pUdk, ctx, false);
        if (mn.getFromType()==0) {//-------------------------------------------//一.1-从服务器来的消息，对于服务器，先到的占用。
            if (bindDeviceFlag) {//处理注册
                //全局处理
                globalMem.bindPushUserANDSocket(pUdk, ctx);
                //本Channel处理
                c_PUDK.set(pUdk);
                c_DevK.set(pUdk.getDeviceId()+"="+pUdk.getPCDType());
            } else {//与之前的注册不一致，关闭掉这个Socket
                MsgNormal ackM=MessageUtils.buildAckEntryMsg(mn);
                ackM.setReturnType(0);//失败
                ackM.setSendTime(System.currentTimeMillis());
                ackM.setUserId(pConf.get_ServerType());
                ackM.setDeviceId(pConf.get_ServerName());
                ctx.writeAndFlush(ackM);
                ctx.channel().bytesBeforeUnwritable();
                ctx.channel().bytesBeforeWritable();
                ctx.close();
            }
        } else {//-------------------------------------------------------------//一.2-从设备端来的消息，对于服务器，先到的占用。
            if (!bindDeviceFlag) { //与原来记录的不一致，则删除原来的，对于客户端，后到的占用。
                ChannelHandlerContext _ctx=(ChannelHandlerContext)globalMem.getSocketByDevice(pUdk);
                _ctx.close();
                bindDeviceFlag=globalMem.bindDeviceANDsocket(pUdk, ctx, true);
            }
            Map<String, Object> retM=sessionService.dealUDkeyEntry(pUdk, "socket/entry");
            if (!(""+retM.get("ReturnType")).equals("1001")) {
                MsgNormal ackM=MessageUtils.buildAckEntryMsg(mn);
                ackM.setReturnType(0);//失败
                ackM.setUserId(pConf.get_ServerType());
                ackM.setDeviceId(pConf.get_ServerName());
                ackM.setSendTime(System.currentTimeMillis());
                ctx.writeAndFlush(ackM);
            } else {//登录成功
                pUdk.setUserId(""+retM.get("UserId"));
                c_PUDK.set(pUdk);
                c_DevK.set(pUdk.getDeviceId()+"="+pUdk.getPCDType());
                MsgNormal ackM=MessageUtils.buildAckEntryMsg(mn);
                ackM.setReturnType(1);//成功
                ackM.setUserId(pConf.get_ServerType());
                ackM.setDeviceId(pConf.get_ServerName());
                ackM.setSendTime(System.currentTimeMillis());
                ctx.writeAndFlush(ackM);

                //判断踢出
                ChannelHandlerContext _oldSh=(ChannelHandlerContext)globalMem.getSocketByUser(pUdk); //和该用户对应的旧的Socket处理
                PushUserUDKey _oldUk=(_oldSh==null?null:(_oldSh.channel().attr(CHANNEL_PUDKEY)).get()); //旧Socket处理所绑定的UserKey
                if (_oldSh!=null&&_oldUk!=null&&!_oldSh.equals(ctx)  //1-旧Socket处理不为空；2-旧Socket处理中绑定用户Key不为空；3-新旧Socket处理不相等
                  &&_oldUk.getPCDType()==pUdk.getPCDType() //新旧Socket对应设备类型相同
                  &&_oldUk.getUserId().equals(pUdk.getUserId()) //新旧Socket对应用户相同
                  &&!_oldUk.getDeviceId().equals(pUdk.getDeviceId())
                  &&sessionService.needKickOut(_oldUk) //旧账号已被踢出
                ) {//踢出
                    if (globalMem.kickOut(pUdk, _oldSh)) {
                        MsgNormal kickOutMsg=new MsgNormal();
                        kickOutMsg.setMsgId(SequenceUUID.getUUIDSubSegment(4));
                        kickOutMsg.setMsgType(0);
                        kickOutMsg.setAffirm(0);
                        kickOutMsg.setFromType(0);
                        kickOutMsg.setToType(1);
                        kickOutMsg.setBizType(0x04);
                        kickOutMsg.setCmdType(3);
                        kickOutMsg.setCommand(1);
                        kickOutMsg.setUserId(pConf.get_ServerType());
                        kickOutMsg.setDeviceId(pConf.get_ServerName());
                        kickOutMsg.setSendTime(System.currentTimeMillis());
                        Map<String, Object> dataMap=new HashMap<String, Object>();
                        dataMap.put("UserId", pUdk.getUserId());
                        dataMap.put("PCDType", pUdk.getPCDType());
                        dataMap.put("DeviceId", pUdk.getDeviceId());
                        MapContent mc=new MapContent(dataMap);
                        kickOutMsg.setMsgContent(mc);
                        _oldSh.writeAndFlush(kickOutMsg);
                    }
                }
                globalMem.bindPushUserANDSocket(pUdk, ctx);//绑定信息

                //发送注册成功的消息给组对讲和电话——以便他处理组在线的功能
                //对讲组
                mn.setAffirm(0);//设置为不需要回复
                mn.setBizType(1);//设置为组消息
                mn.setCmdType(3);//组通知
                mn.setCommand(0);
                mn.setUserId(pUdk.getUserId());
                globalMem.receiveMem.putTypeMsg(""+((MsgNormal)mn).getBizType(), mn);
                //电话
                MsgNormal msc=MessageUtils.clone(mn);
                msc.setMsgId(mn.getMsgId());
                msc.setBizType(2);
                globalMem.receiveMem.putTypeMsg(""+msc.getBizType(), msc);
            }
        }
    }
}