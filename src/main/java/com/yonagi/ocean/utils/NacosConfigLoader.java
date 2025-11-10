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
import com.yonagi.ocean.spi.ConfigRecoveryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/06 12:42
 */
public class NacosConfigLoader {

    private static class ListenerRegistration {
        final String dataId;
        final String group;
        final Listener listener;

        public ListenerRegistration(String dataId, String group, Listener listener) {
            this.dataId = dataId;
            this.group = group;
            this.listener = listener;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(NacosConfigLoader.class);
    private static volatile ConfigService configService = null;
    private static AtomicBoolean initialized = new AtomicBoolean(false);

    private static boolean nacosEnabled;
    private static int maxRetries;
    private static int retryInterval;
    private static boolean reconnectEnabled;
    private static int reconnectIntervalSeconds;

    private static List<ConfigRecoveryAction> recoveryActions = Collections.synchronizedList(new ArrayList<>());
    private static List<ListenerRegistration> failedListeners = Collections.synchronizedList(new ArrayList<>());
    private static ScheduledExecutorService scheduler;
    private static final String SCHEDULER_NAME = "Nacos-Reconnect-Scheduler";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void init() {
        if (initialized.getAndSet(true)) {
            return;
        }
        nacosEnabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("nacos.enabled"));
        maxRetries = Integer.parseInt(LocalConfigLoader.getProperty("nacos.max_retries"));
        retryInterval = Integer.parseInt(LocalConfigLoader.getProperty("nacos.retry_interval_ms"));
        reconnectEnabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("nacos.reconnect.enabled", "true"));
        reconnectIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("nacos.reconnect.inverval_seconds", "300"));
        String serverAddr = LocalConfigLoader.getProperty("nacos.server_addr");

        if (!nacosEnabled) {
            log.warn("Nacos disabled in config, using local config only.");
            initialized.set(true);
            return;
        }
        if (tryConnectNacos(serverAddr)) {
            log.info("Nacos ConfigService successfully initialized on startup");
        } else {
            log.error("Nacos server is unreachable at {}", serverAddr);
            if (reconnectEnabled) {
                log.info("Nacos Auto-reconnection is enabled. Starting retry polling every {} seconds", reconnectIntervalSeconds);
                startNacosReconnectPolling(serverAddr);
            } else {
                log.info("Nacos Auto-reconnection is disabled. Configuration will remain local until restart");
            }
        }
    }

    private static boolean tryConnectNacos(String serverAddr) {
        if (!checkNacosConnectivity(serverAddr)) {
            return false;
        }
        Properties props = new Properties();
        props.put("serverAddr", serverAddr);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ConfigService tempConfigService = NacosFactory.createConfigService(props);
                tempConfigService.getServerStatus();

                configService = tempConfigService;
                log.info("Nacos ConfigService created and connected (attempt {}/{})", attempt, maxRetries);
                return true;
            } catch (Exception e) {
                log.warn("Attempt {}/{}: Failed to connect Nacos - {}", attempt, maxRetries, e.getMessage());
                try {
                    Thread.sleep((long)retryInterval * attempt);
                } catch (InterruptedException ignored) {}
            }
        }
        return false;
    }

    public static boolean checkNacosConnectivity(String serverAddr) {
        String[] parts = serverAddr.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 8848;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port));
                return true;
            } catch (IOException e) {
                log.warn("Attempt {}/{}: Cannot connect to Nacos server at {}:{}", attempt, maxRetries, host, port);
                try {
                    if (attempt < maxRetries) {
                        Thread.sleep((long) retryInterval * (attempt));
                    }
                } catch (InterruptedException ignored) {

                }
            }
        }
        return false;
    }

    public static void registerRecoveryAction(ConfigRecoveryAction action) {
        if (configService != null) {
            action.recover(configService);
        } else {
            recoveryActions.add(action);
        }
    }

    private static void startNacosReconnectPolling(String serverAddr) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName(SCHEDULER_NAME);
            t.setDaemon(true);
            return t;
        });
        Runnable reconnectTask = () -> {
            if (configService != null) {
                scheduler.shutdown();
                return;
            }
            if (tryConnectNacos(serverAddr)) {
                triggerRecoveryActions();
            }
        };

        scheduler.scheduleAtFixedRate(reconnectTask, reconnectIntervalSeconds, reconnectIntervalSeconds, TimeUnit.SECONDS);
        log.warn("Nacos reconnect polling started. Retry interval: {}s", reconnectIntervalSeconds);
    }

    private static void triggerRecoveryActions() {
        if (configService == null) {
            return;
        }
        log.info("Nacos reconnected successfully! Triggering {} config recovery actions", recoveryActions.size());

        ConfigService finalConfigService = configService;

        synchronized (recoveryActions) {
            for (ConfigRecoveryAction action : recoveryActions) {
                try {
                    action.recover(finalConfigService);
                } catch (Exception e) {
                    log.error("Nacos recovery action failed for one source: {}", e.getMessage(), e);
                }
            }
            recoveryActions.clear();
        }
        synchronized (failedListeners) {
            for (ListenerRegistration reg : failedListeners) {
                try {
                    configService.addListener(reg.dataId, reg.dataId, reg.listener);
                } catch (Exception e) {
                    log.error("Failed to re-register listener on recovery: {}", e.getMessage(), e);
                }
            }
            failedListeners.clear();
        }
        scheduler.shutdown();
    }

    public static ConfigService getConfigService() {
        return configService;
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
                    log.warn("Fail to fetch config from Nacos, attempt {}/{}", attempt + 1, maxRetries, e);
                }
                attempt++;
                Thread.sleep((long) retryInterval * attempt);
            }
        } catch (InterruptedException ignored) {

        }
        log.error("Could not fetch config from Nacos after \" + maxRetries + \" attempts. Using local config.");
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
                    log.warn("Fail to fetch config from Nacos, attempt {}/{}", attempt + 1, maxRetries, e);
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
        log.error("Could not fetch config from Nacos after {} attempts. Using local config.", maxRetries);
        return null;
    }

    public static void addListener(String dataId, String group, Listener listener) {
        if (!nacosEnabled) {
            log.warn("Nacos not available, listener not registered for [{}:{}]", group, dataId);
            return;
        }
        if (configService != null) {
            try {
                configService.addListener(dataId, group, listener);
                log.info("Listener added for [{}:{}]", group, dataId);
            } catch (Exception e) {
                log.error("Failed to add listener [{}:{}]", group, dataId, e);
                failedListeners.add(new ListenerRegistration(dataId, group, listener));
            }
        } else {
            failedListeners.add(new ListenerRegistration(dataId, group, listener));
        }
    }
}
