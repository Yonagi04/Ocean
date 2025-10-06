package com.yonagi.ocean.utils;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
    private static volatile ConfigService configService;

    private static boolean nacosEnabled;
    private static int maxRetries;
    private static int retryInterval;

    public static void init() {
        nacosEnabled = Boolean.parseBoolean(ConfigLoader.getProperty("nacos.enabled"));
        maxRetries = Integer.parseInt(ConfigLoader.getProperty("nacos.max_retries"));
        retryInterval = Integer.parseInt(ConfigLoader.getProperty("nacos.retry_interval_ms"));

        if (!nacosEnabled) {
            log.warn("Nacos disabled in configuration, using local configuration only.");
            return;
        }
        synchronized (NacosConfigLoader.class) {
            if (configService != null) {
                return;
            }
            String serverAddr = ConfigLoader.getProperty("nacos.server_addr");
            if (!checkNacosConnectivity(serverAddr)) {
                log.error("Nacos server is unreachable at {}. Falling back to local config.", serverAddr);
                return;
            }
            Properties props = new Properties();
            props.put("serverAddr", serverAddr);
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    configService = NacosFactory.createConfigService(props);
                    log.info("Nacos ConfigService created (attempt {}/{})", attempt, maxRetries);

                    configService.getServerStatus();
                    log.info("Successfully connected to Nacos Server");
                    return;
                } catch (Exception e) {
                    log.warn("Attempt {}/{}: Failed to connect Nacos - {}", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep((long)retryInterval * attempt);
                    } catch (InterruptedException ignored) {}
                }
            }
            configService = null;
            log.error("Failed to connect to Nacos after {} attemps. Falling back to local config.", maxRetries);
        }
    }

    private static boolean checkNacosConnectivity(String serverAddr) {
        String[] parts = serverAddr.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 8848;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port));
                return true;
            } catch (IOException e) {
                log.warn("Attempt {}/{}: Cannot connect to Nacos server at {}:{}", attempt + 1, maxRetries, host, port);
                try {
                    Thread.sleep((long) retryInterval * (attempt + 1));
                } catch (InterruptedException ignored) {

                }
            }
        }
        return false;
    }

    public static String getConfig(String dataId, String group, long timeoutMs) {
        if (configService == null) {
            log.warn("Nacos ConfigService is not initialized");
            return null;
        }
        try {
            int attempt = 0;
            int maxRetries = Integer.parseInt(ConfigLoader.getProperty("nacos.max_retries"));
            int retryInterval = Integer.parseInt(ConfigLoader.getProperty("nacos.retry_interval_ms"));
            while (attempt < maxRetries) {
                try {
                    String config = configService.getConfig(dataId, group, timeoutMs);
                    if (config != null) {
                        return config;
                    }
                } catch (NacosException e) {
                    log.warn("Fail to fetch configuration from Nacos, attempt {}/{}", attempt + 1, maxRetries, e);
                }
                attempt++;
                Thread.sleep((long) retryInterval * attempt);
            }
        } catch (InterruptedException ignored) {

        }
        log.error("Could not fetch config from Nacos after \" + maxRetries + \" attempts. Using local config.");
        return null;
    }

    public static void addListener(String dataId, String group, Listener listener) {
        if (!nacosEnabled || configService == null) {
            log.warn("Nacos not available, listener not registered for [{}:{}]", group, dataId);
            return;
        }
        try {
            configService.addListener(dataId, group, listener);
            log.info("Listener added for [{}:{}]", group, dataId);
        } catch (Exception e) {
            log.error("Failed to add listener [{}:{}]", group, dataId, e);
        }
    }
}
