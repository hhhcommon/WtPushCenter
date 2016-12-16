package com.woting.push.core.message.content;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.spiritdata.framework.util.JsonUtils;
import com.woting.push.core.message.MessageContent;

/**
 * 消息体的内容
 * @author wanghui
 */
public class MapContent implements MessageContent, Serializable {
    private static final long serialVersionUID=1772778270294321854L;

    /**
     * 消息内容
     */
    private Map<String, Object> contentMap;

    /**
     * 构造函数
     */
    public MapContent() {
    }
    /**
     * 构造函数
     * @param contentMap 设置消息体
     */
    public MapContent(Map<String, Object> contentMap) {
        this.contentMap=contentMap;
    }

    public Map<String, Object> getContentMap() {
        return contentMap;
    }

    public void setContentMap(Map<String, Object> contentMap) {
        this.contentMap=contentMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void fromBytes(byte[] content) throws UnsupportedEncodingException {
        String json=new String(content, "utf-8");
        contentMap=(Map<String, Object>) JsonUtils.jsonToObj(json, Map.class);
    }

    @Override
    public byte[] toBytes() throws UnsupportedEncodingException {
        String jsonStr=JsonUtils.objToJson(contentMap);
        return jsonStr.getBytes("utf-8");
    }

    public Object get(String key) {
        if (contentMap==null) return null;
        return contentMap.get(key);
    }
    @Override
    public boolean equals(MessageContent mc) {
        if (mc==null) return false;
        if (!(mc instanceof MapContent)) return false;

        MapContent _mc=(MapContent)mc;
       
        if (contentMap==null&&_mc.contentMap==null) return true;
        if (contentMap!=null&&_mc.contentMap==null) return false;
        if (contentMap==null&&_mc.contentMap!=null) return false;

        Map<String, Object> _contentMap=_mc.contentMap;
        if (contentMap.size()==_contentMap.size()) {
            for (Object _key1:contentMap.keySet()) {
                Object _v1=contentMap.get(_key1);
                Object _v2=_contentMap.get(_key1);
                if (_v1!=null&&_v2!=null) {
                    boolean compared=false;
                    try {
                        if (!_v1.equals(_v2)) return false;
                        compared=true;
                    } catch(Exception e) {}
                    if (!compared&&_v1!=_v2) return false;
                }
                if ((_v1!=null&&_v2==null)||(_v1==null&&_v2!=null)) return false;
            }
            return true;
        }
        return false;
    }
}