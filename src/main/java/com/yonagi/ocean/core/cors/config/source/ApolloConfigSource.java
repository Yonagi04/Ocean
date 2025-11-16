package com.yonagi.ocean.core.cors.config.source;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.yonagi.ocean.backup.BackupScheduler;
import com.yonagi.ocean.core.cors.config.CorsConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 19:03
 */
public class ApolloConfigSource implements ConfigSource {

    private Runnable callback;

    private String namespace;
    private Boolean enabled;
    private Config config;

    private static final Logger log = LoggerFactory.getLogger(ApolloConfigSource.class);

    public ApolloConfigSource() {
        this.namespace = LocalConfigLoader.getProperty("server.cors.apollo.namespace", "application");
        this.enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        this.config = ConfigService.getConfig(namespace);
        startPeriodicSync();
    }

    @Override
    public CorsConfig load() {
        if (!enabled || config == null) {
            return null;
        }
        boolean enabled = Boolean.parseBoolean(config.getProperty("server.cors.enabled", "false"));
        String allowOrigin = config.getProperty("server.cors.allow_origin", "*");
        String allowMethods = config.getProperty("server.cors.allow_methods", "*");
        String allowHeaders = config.getProperty("server.cors.allow_headers", "*");
        String exposeHeaders = config.getProperty("server.cors.expose_headers", "*");
        boolean allowCredentials = Boolean.parseBoolean(config.getProperty("server.cors.allow_credentials", "false"));
        int maxAge = Integer.parseInt(config.getProperty("server.cors.max_age", "3600"));

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
        config.addChangeListener(configChangeEvent -> {
            if (callback != null) {
                callback.run();
            }
        });
    }

    private void startPeriodicSync() {
        boolean enableApollo = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        boolean enableSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cors.apollo.sync_to_local", "true"));
        if (!enableApollo || !enableSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.cors.apollo.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.cors.apollo.sync_local_path");
        BackupScheduler.startApolloTask(namespace, syncLocalPath, syncIntervalSeconds);
    }
}
