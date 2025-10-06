package com.yonagi.ocean.utils;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.WebSocket;
import java.util.Properties;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/06 12:42
 */
public class NacosConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigLoader.class);
    private static ConfigService configService;

    static {
        try {
            boolean nacosEnabled = Boolean.parseBoolean(ConfigLoader.getProperty("nacos.enabled"));
            if (nacosEnabled) {
                Properties properties = new Properties();
                properties.put("serverAddr", ConfigLoader.getProperty("nacos.serverAddr"));
                configService = NacosFactory.createConfigService(properties);
                log.info("Configuration from Nacos enabled");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Nacos ConfigService", e);
        }
    }

    public static String getConfig(String dataId, String group, long timeoutMs) {
        if (configService == null) {
            log.warn("Nacos ConfigService is not initialized");
            return null;
        }
        try {
            return configService.getConfig(dataId, group, timeoutMs);
        } catch (NacosException e) {
            log.error("Failed to get config from Nacos", e);
            return null;
        }
    }

    public static void addListener(String dataId, String group, Listener listener) {
        if (!Boolean.parseBoolean(ConfigLoader.getProperty("nacos.enabled"))) {
            return;
        }
        try {
            configService.addListener(dataId, group, listener);
        } catch (Exception e) {
            log.error("Failed to add listener to Nacos Config", e);
        }
    }
}
