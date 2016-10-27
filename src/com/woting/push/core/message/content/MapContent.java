package com.woting.push.core.message.content;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import com.spiritdata.framework.util.JsonUtils;
import com.woting.push.core.message.MessageContent;

public class MapContent implements MessageContent, Serializable {
    private static final long serialVersionUID = 1772778270294321854L;

    private Map<String, Object> contentMap;

    public MapContent() {
    }
    public MapContent(Map<String, Object> contentMap) {
        this.contentMap=contentMap;
    }

    public Map<String, Object> getContentMap() {
        return contentMap;
    }

    public void setContentMap(Map<String, Object> contentMap) {
        this.contentMap = contentMap;
    }

    @Override
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
}