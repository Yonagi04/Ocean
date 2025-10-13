package com.yonagi.ocean.core.configuration.source.cors;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.yonagi.ocean.core.configuration.CorsConfig;
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
 * @date 2025/10/13 14:51
 */
public class NacosConfigSource implements ConfigSource {

    private Runnable callback;

    private ConfigService configService;
    private String dataId;
    private String group;
    private int timeoutMs;

    private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);

    public NacosConfigSource(ConfigService configService) {
        this.configService = configService;
        this.dataId = LocalConfigLoader.getProperty("server.cors.nacos.data_id");
        this.group = LocalConfigLoader.getProperty("server.cors.nacos.group");
        this.timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
        startPeriodicSync();
    }

    @Override
    public CorsConfig load() {
        Properties props = NacosConfigLoader.getPropertiesConfig(dataId, group, timeoutMs);
        if (props == null) {
            return null;
        }
        boolean enabled = Boolean.parseBoolean(props.getProperty("server.cors.enabled", "false"));
        String allowOrigin = props.getProperty("server.cors.allow_origin", "*");
        String allowMethods = props.getProperty("server.cors.allow_methods", "*");
        String allowHeaders = props.getProperty("server.cors.allow_headers", "*");
        String exposeHeaders = props.getProperty("server.cors.expose_headers", "*");
        boolean allowCredentials = Boolean.parseBoolean(props.getProperty("server.cors.allow_credentials", "false"));
        int maxAge = Integer.parseInt(props.getProperty("server.cors.max_age", "3600"));

        if (maxAge < 0) {
            log.warn("maxAge is invalid, reset to default value: 3600");
            maxAge = 3600;
        }
        return new CorsConfig.Builder()
                .enabled(enabled)
                .allowOrigin(allowOrigin)
                .allowMethods(allowMethods)
                .allowHeaders(allowHeaders)
                .exposeHeaders(exposeHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAge)
                .build();
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
        boolean enabledSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cors.nacos.sync_to_local", "false"));

        if (!enabledNacos || !enabledSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.cors.nacos.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.cors.nacos.sync_local_path");
        NacosBackupScheduler.start(dataId, group, syncLocalPath, syncIntervalSeconds, timeoutMs);
    }
}
