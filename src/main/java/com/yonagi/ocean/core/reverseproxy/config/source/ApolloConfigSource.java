package com.yonagi.ocean.core.reverseproxy.config.source;

import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.backup.BackupScheduler;
import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 19:42
 */
public class ApolloConfigSource implements ConfigSource {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ApolloConfigSource.class);

    private Runnable callback;

    private String namespace;
    private Boolean enabled;
    private ConfigFile configFile;

    public ApolloConfigSource() {
        this.namespace = LocalConfigLoader.getProperty("server.reverse_proxy.apollo.namespace", "application");
        this.enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        this.configFile = ConfigService.getConfigFile(namespace, ConfigFileFormat.JSON);
        startPeriodicSync();
    }

    @Override
    public List<ReverseProxyConfig> load() {
        if (!enabled || configFile == null) {
            return null;
        }
        String content = configFile.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        List<ReverseProxyConfig> configs;
        try {
            configs = objectMapper.readValue(content, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse reverse proxy config from Apollo: {}", e.getMessage(), e);
            return null;
        }
        return configs;
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        configFile.addChangeListener(configFileChangeEvent -> {
            if (callback != null) {
                callback.run();
            }
        });
    }

    private void startPeriodicSync() {
        boolean enableApollo = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        boolean enableSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.reverse_proxy.apollo.sync_to_local", "true"));
        if (!enableApollo || !enableSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.reverse_proxy.apollo.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.reverse_proxy.apollo.sync_local_path");
        BackupScheduler.startApolloTask(namespace, syncLocalPath, syncIntervalSeconds);
    }
}
