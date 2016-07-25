package com.woting.jsonconfig;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonParser;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spiritdata.framework.util.StringUtils;

/**
 * 以json为格式的配置文件。
 * <pre>
 * 配置文件的json不支持数组，若是数组，所对应的key的值为错误
 * </pre>
 * @author wanghui
 */

public class JsonConfig {
    private boolean isLoaded=false;
    private Map<String, String> configSets=null;

    //以下为公用方法
    public JsonConfig() {
        isLoaded=false;
        configSets=new HashMap<String, String>();
    }

    /**
     * 初始化Json配置文件类。
     * <pre>
     * 并从
     * </pre>
     * @param jsonFileName
     * @throws IOException 
     * @throws JsonProcessingException 
     */
    public JsonConfig(String jsonFileName) throws IOException {
        configSets=new HashMap<String, String>();
        loader(jsonFileName);
    }

    /**
     * 得到配置项key的整数值
     * @param key 配置项key
     * @return 整数值
     */
    public int getInt(String key) {
        if (!isLoaded) throw new RuntimeException("未加载完成，不能读取");
        return Integer.parseInt(configSets.get(key));
    }

    /**
     * 得到配置项key的长整型值
     * @param key 配置项key
     * @return 长整型值
     */
    public long getLong(String key) {
        if (!isLoaded) throw new RuntimeException("未加载完成，不能读取");
        return Long.parseLong(configSets.get(key));
    }

    /**
     * 得到配置项key的浮点数值
     * @param key 配置项key
     * @return 浮点数值
     */
    public float getFloat(String key) {
        if (!isLoaded) throw new RuntimeException("未加载完成，不能读取");
        return Float.parseFloat(configSets.get(key));
    }

    /**
     * 得到配置项key的字符串值
     * @param key 配置项key
     * @return 字符串值
     */
    public String getString(String key) {
        if (!isLoaded) throw new RuntimeException("未加载完成，不能读取");
        return configSets.get(key);
    }

    //以下私有方法
    private void loader(String jsonFileName) throws IOException {
        isLoaded=false;
        String jsonStr=FileUtils.readFileToString(new File(jsonFileName), "utf-8");
        while (!jsonStr.substring(0, 1).equals("{")) jsonStr=jsonStr.substring(1);
        jsonStr=jsonStr.replaceAll("(?<!:)\\/\\/.*|\\/\\*(\\s|.)*?\\*\\/", "");

        ObjectMapper mapper=new ObjectMapper();
        //单引号
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        //无引号
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        //特殊字符
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap=(Map<String, Object>)mapper.readValue(jsonStr, Map.class);
        parse(configMap, null);
        isLoaded=true;
    }
    @SuppressWarnings("unchecked")
    private void parse(Map<String, Object> configMap, String rootStr) {
        if (configMap==null||configMap.isEmpty()) return;
        Object value;
        for (String key: configMap.keySet()) {
            value=configMap.get(key);
            if (value instanceof String) {
                configSets.put((StringUtils.isNullOrEmptyOrSpace(rootStr)?"":(rootStr+"."))+key, value+"");
            } else if (value instanceof Map) {
                parse((Map<String, Object>)value, key);
            } else if (value instanceof List) {
                configSets.put((StringUtils.isNullOrEmptyOrSpace(rootStr)?"":(rootStr+"."))+key, "ErrList");
            } else {
                configSets.put((StringUtils.isNullOrEmptyOrSpace(rootStr)?"":(rootStr+"."))+key, value+"");
            }
        }
    }
}