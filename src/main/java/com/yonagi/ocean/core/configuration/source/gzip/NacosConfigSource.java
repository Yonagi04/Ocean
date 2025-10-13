package com.yonagi.ocean.core.configuration.source.gzip;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.yonagi.ocean.core.configuration.GzipConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosBackupScheduler;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 10:52
 */
public class NacosConfigSource implements ConfigSource {

    private Runnable callback;

    private ConfigService configService;
    private String dataId;
    private String group;
    private int timeoutMs;

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);
    private static final String DEFAULT_MIN_CONTENT_LENGTH = "1024";
    private static final String DEFAULT_COMPRESSION_LEVEL = "6";

    public NacosConfigSource(ConfigService configService) {
        this.configService = configService;
        this.dataId = LocalConfigLoader.getProperty("server.gzip.nacos.data_id");
        this.group = LocalConfigLoader.getProperty("server.gzip.nacos.group");
        this.timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
        startPeriodicSync();
    }

    @Override
    public GzipConfig load() {
        Properties props = NacosConfigLoader.getPropertiesConfig(dataId, group, timeoutMs);
        if (props == null) {
            return null;
        }

        boolean enabled = Boolean.parseBoolean(props.getProperty("server.gzip.enabled", "false"));
        int minContentLength = Integer.parseInt(props.getProperty("server.gzip.min_content_length", DEFAULT_MIN_CONTENT_LENGTH));
        int compressionLevel = Integer.parseInt(props.getProperty("server.gzip.compression_level", DEFAULT_COMPRESSION_LEVEL));

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
        NacosConfigLoader.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configContent) {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }

    private void startPeriodicSync() {
        boolean enabledNacos = Boolean.parseBoolean(LocalConfigLoader.getProperty("nacos.enabled", "false"));
        boolean enabledSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.gzip.nacos.sync_to_local", "false"));

        if (!enabledNacos || !enabledSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.gzip.nacos.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.gzip.nacos.sync_local_path");
        NacosBackupScheduler.start(dataId, group, syncLocalPath, syncIntervalSeconds, timeoutMs);
    }
}
