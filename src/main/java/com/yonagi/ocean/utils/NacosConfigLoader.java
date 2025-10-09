package com.yonagi.ocean.utils;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
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
    private static volatile boolean initialized = false;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void init() {
        if (initialized) {
            return;
        }
        nacosEnabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("nacos.enabled"));
        maxRetries = Integer.parseInt(LocalConfigLoader.getProperty("nacos.max_retries"));
        retryInterval = Integer.parseInt(LocalConfigLoader.getProperty("nacos.retry_interval_ms"));

        if (!nacosEnabled) {
            log.warn("Nacos disabled in configuration, using local configuration only.");
            initialized = true;
            return;
        }
        synchronized (NacosConfigLoader.class) {
            if (configService != null) {
                initialized = true;
                return;
            }
            String serverAddr = LocalConfigLoader.getProperty("nacos.server_addr");
            if (!checkNacosConnectivity(serverAddr)) {
                log.error("Nacos server is unreachable at {}. Falling back to local configuration.", serverAddr);
                initialized = true;
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
                    initialized = true;
                    return;
                } catch (Exception e) {
                    log.warn("Attempt {}/{}: Failed to connect Nacos - {}", attempt, maxRetries, e.getMessage());
                    try {
                        Thread.sleep((long)retryInterval * attempt);
                    } catch (InterruptedException ignored) {}
                }
            }
            configService = null;
            log.error("Failed to connect to Nacos after {} attemps. Falling back to local configuration.", maxRetries);
            initialized = true;
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

    public static Properties getPropertiesConfig(String dataId, String group, long timeoutMs) {
        if (configService == null) {
            log.warn("Nacos ConfigService is not initialized");
            return null;
        }
        try {
            int attempt = 0;
            int maxRetries = Integer.parseInt(LocalConfigLoader.getProperty("nacos.max_retries"));
            int retryInterval = Integer.parseInt(LocalConfigLoader.getProperty("nacos.retry_interval_ms"));
            while (attempt < maxRetries) {
                try {
                    String configContent = configService.getConfig(dataId, group, timeoutMs);
                    if (configContent != null) {
                        Properties props = new Properties();
                        try (StringReader reader = new StringReader(configContent)) {
                            props.load(reader);
                        } catch (IOException e) {
                            log.error("Nacos ConfigService failed to load properties from {}", configContent);
                            return null;
                        }
                        return props;
                    }
                } catch (NacosException e) {
                    log.warn("Fail to fetch configuration from Nacos, attempt {}/{}", attempt + 1, maxRetries, e);
                }
                attempt++;
                Thread.sleep((long) retryInterval * attempt);
            }
        } catch (InterruptedException ignored) {

        }
        log.error("Could not fetch configuration from Nacos after \" + maxRetries + \" attempts. Using local configuration.");
        return null;
    }

    public static ArrayNode getJsonArrayConfig(String dataId, String group, long timeoutMs) {
        if (configService == null) {
            log.warn("Nacos ConfigService is not initialized");
            return null;
        }
        try {
            int attempt = 0;
            int maxRetries = Integer.parseInt(LocalConfigLoader.getProperty("nacos.max_retries"));
            int retryInterval = Integer.parseInt(LocalConfigLoader.getProperty("nacos.retry_interval_ms"));
            while (attempt < maxRetries) {
                try {
                    String configContent = configService.getConfig(dataId, group, timeoutMs);
                    if (configContent != null) {
                        JsonNode rootNode = objectMapper.readTree(configContent);
                        if (rootNode.isArray()) {
                            return (ArrayNode) rootNode;
                        } else {
                            return null;
                        }
                    }
                } catch (NacosException e) {
                    log.warn("Fail to fetch configuration from Nacos, attempt {}/{}", attempt + 1, maxRetries, e);
                } catch (JsonMappingException e) {
                    log.error("Nacos ConfigService returned invalid JSON array: {}", e.getMessage());
                    return null;
                } catch (JsonProcessingException e) {
                    log.error("Nacos ConfigService failed to parse JSON: {}", e.getMessage());
                    return null;
                }
                attempt++;
                Thread.sleep((long) retryInterval * attempt);
            }
        } catch (InterruptedException ignored) {

        }
        log.error("Could not fetch configuration from Nacos after {} attempts. Using local configuration.", maxRetries);
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
