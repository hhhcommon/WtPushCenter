package com.woting.push.core.message.content;

import java.util.Map;

import com.spiritdata.framework.util.JsonUtils;
import com.woting.push.core.message.MessageContent;

public class MapContent implements MessageContent {
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
    public void fromBytes(byte[] content) {
        String json=new String(content);
        contentMap=(Map<String, Object>) JsonUtils.jsonToObj(json, Map.class);
    }

    @Override
    public byte[] toBytes() {
        String jsonStr=JsonUtils.objToJson(contentMap);
        return jsonStr.getBytes();
    }
}