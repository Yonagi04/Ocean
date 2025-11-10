package com.yonagi.ocean.core.ratelimiter.config.source;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yonagi.ocean.core.ratelimiter.config.RateLimitConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.backup.NacosBackupScheduler;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 11:47
 */
public class NacosConfigSource implements ConfigSource {

    private Runnable callback;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);

    private ConfigService configService;
    private String dataId;
    private String group;
    private int timeoutMs;

    public NacosConfigSource(ConfigService configService) {
        this.configService = configService;
        this.dataId = LocalConfigLoader.getProperty("server.rate_limit.nacos.data_id");
        this.group = LocalConfigLoader.getProperty("server.rate_limit.nacos.group");
        this.timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
        startPeriodicSync();
    }

    @Override
    public List<RateLimitConfig> load() {
        try {
            String dataId = LocalConfigLoader.getProperty("server.rate_limit.nacos.data_id");
            String group = LocalConfigLoader.getProperty("server.rate_limit.nacos.group");
            int timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
            ArrayNode jsonArrayConfig = NacosConfigLoader.getJsonArrayConfig(dataId, group, timeoutMs);
            if (jsonArrayConfig == null) {
                return null;
            }
            TypeReference<List<RateLimitConfig>> typeRef = new TypeReference<>() {};
            List<RateLimitConfig> rateLimitConfigs = mapper.convertValue(jsonArrayConfig, typeRef);
            log.info("Loaded {} rate limit configurations from Nacos config", rateLimitConfigs.size());
            for (RateLimitConfig config : rateLimitConfigs) {
                log.info("RateLimit: {} {}, Scope size: {} (enabled: {})",
                        config.getMethod(), config.getPath(), config.getScopes().size(), config.isEnabled());
            }
            return rateLimitConfigs;
        } catch (Exception e) {
            log.error("Failed to load rate limit config from Nacos: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        NacosConfigLoader.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String s) {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }

    private void startPeriodicSync() {
        boolean enabledNacos = Boolean.parseBoolean(LocalConfigLoader.getProperty("nacos.enabled", "false"));
        boolean enabledSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.rate_limit.nacos.sync_to_local", "true"));
        if (!enabledNacos || !enabledSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.rate_limit.nacos.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.rate_limit.nacos.sync_local_path");
        NacosBackupScheduler.start(dataId, group, syncLocalPath, syncIntervalSeconds, timeoutMs);
    }
}
