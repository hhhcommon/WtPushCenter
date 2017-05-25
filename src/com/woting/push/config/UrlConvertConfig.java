package com.woting.push.config;

import java.util.Map;

import com.woting.push.core.config.Config;

public class UrlConvertConfig implements Config {
    private Map<String, String> convertRules;

    public Map<String, String> getConvertRules() {
        return convertRules;
    }

    public void setConvertRules(Map<String, String> convertRules) {
        this.convertRules = convertRules;
    }
}