package com.yonagi.ocean.core.gzip.config.source;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.yonagi.ocean.core.gzip.config.GzipConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 19:16
 */
public class ApolloConfigSource implements ConfigSource {

    private Runnable callback;

    private String namespace;
    private Boolean enabled;
    private Config config;

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);
    private static final String DEFAULT_MIN_CONTENT_LENGTH = "1024";
    private static final String DEFAULT_COMPRESSION_LEVEL = "6";

    public ApolloConfigSource() {
        this.namespace = LocalConfigLoader.getProperty("server.gzip.apollo.namespace", "application");
        this.enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        startPeriodicSync();
    }

    @Override
    public GzipConfig load() {
        if (!enabled) {
            return null;
        }
        config = ConfigService.getConfig(namespace);
        if (config == null) {
            return null;
        }

        boolean enabled = Boolean.parseBoolean(config.getProperty("server.gzip.enabled", "false"));
        int minContentLength = Integer.parseInt(config.getProperty("server.gzip.min_content_length", DEFAULT_MIN_CONTENT_LENGTH));
        int compressionLevel = Integer.parseInt(config.getProperty("server.gzip.compression_level", DEFAULT_COMPRESSION_LEVEL));
        if (minContentLength < 0) {
            log.warn("minContentLength is invalid. Reset to default value: {}", DEFAULT_MIN_CONTENT_LENGTH);
            minContentLength = Integer.parseInt(DEFAULT_MIN_CONTENT_LENGTH);
        }
        if (compressionLevel < 0 || compressionLevel > 9) {
            log.warn("compressionLevel is invalid, it must to between 1 to 9. Reset to default value: {}", DEFAULT_COMPRESSION_LEVEL);
            compressionLevel = Integer.parseInt(DEFAULT_COMPRESSION_LEVEL);
        }
        return new GzipConfig(enabled, minContentLength, compressionLevel);
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        config.addChangeListener(configChangeEvent -> {
            if (callback != null) {
                callback.run();
            }
        });
    }

    private void startPeriodicSync() {
        boolean enableApollo = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        boolean enableSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.gzip.apollo.sync_to_local", "true"));
        if (!enableApollo || !enableSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.gzip.apollo.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.gzip.apollo.sync_local_path");
        // todo: 实现 Apollo 配置同步到本地文件的逻辑
    }
}
