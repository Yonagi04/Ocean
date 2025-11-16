package com.yonagi.ocean.core.reverseproxy.config.source;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yonagi.ocean.backup.BackupScheduler;
import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
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
 * @date 2025/11/08 16:25
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
        this.dataId = LocalConfigLoader.getProperty("server.reverse_proxy.nacos.data_id");
        this.group = LocalConfigLoader.getProperty("server.reverse_proxy.nacos.group");
        this.timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
        startPeriodicSync();
    }

    @Override
    public List<ReverseProxyConfig> load() {
        try {
            ArrayNode jsonArrayConfig = NacosConfigLoader.getJsonArrayConfig(dataId, group, timeoutMs);
            if (jsonArrayConfig == null) {
                return null;
            }
            TypeReference<List<ReverseProxyConfig>> typeRef = new TypeReference<>() {};
            List<ReverseProxyConfig> reverseProxyConfigs = mapper.convertValue(jsonArrayConfig, typeRef);
            log.info("Loaded {} reverse proxy configurations from Nacos", reverseProxyConfigs.size());
            return reverseProxyConfigs;
        } catch (Exception e) {
            log.error("Failed to load reverse proxy configurations from Nacos: {}", e.getMessage(), e);
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
        boolean enabledSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.reverse_proxy.nacos.sync_to_local", "true"));
        if (!enabledNacos || !enabledSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.reverse_proxy.nacos.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.reverse_proxy.nacos.sync_local_path");
        BackupScheduler.startNacosTask(dataId, group, syncLocalPath, syncIntervalSeconds, timeoutMs);
    }
}
