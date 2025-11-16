package com.yonagi.ocean.core.cors.config.source;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.cors.config.CorsConfig;
import com.yonagi.ocean.enums.RemoteSource;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/15 14:50
 */
public class ConfigManager implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private List<RemoteSource> prioritySources;

    private final Map<RemoteSource, ConfigSource> remoteSources;

    private final LocalConfigSource localSource;

    private final AtomicReference<ConfigSource> activeSource = new AtomicReference<>();

    private Runnable changeCallback;

    public ConfigManager(ConfigService nacosConfigService) {
        String priorityStr = LocalConfigLoader.getProperty("server.cors.remote_sources.priority", "nacos,apollo");
        this.prioritySources = Arrays.stream(priorityStr.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .map(name -> RemoteSource.getAllRemoteSources().stream()
                        .filter(source -> source.getName().equals(name))
                        .findFirst()
                        .orElse(null))
                .collect(Collectors.toList());

        this.remoteSources = new LinkedHashMap<>();
        for (RemoteSource remoteSource : RemoteSource.getAllRemoteSources()) {
            if (prioritySources.contains(remoteSource)) {
                if (remoteSource == RemoteSource.NACOS) {
                    this.remoteSources.put(RemoteSource.NACOS, new NacosConfigSource(nacosConfigService));
                } else if (remoteSource == RemoteSource.APOLLO) {
                    this.remoteSources.put(RemoteSource.APOLLO, new ApolloConfigSource());
                }
            }
        }
        this.localSource = new LocalConfigSource();
        this.activeSource.set(localSource);
        if (prioritySources.contains(RemoteSource.NACOS)) {
            NacosConfigLoader.registerRecoveryAction(configService -> startFailbackCheck(RemoteSource.NACOS.getName(), new NacosConfigSource(configService)));
        }
        this.load();
    }

    @Override
    public CorsConfig load() {
        for (RemoteSource sourceKey : prioritySources) {
            ConfigSource current = remoteSources.get(sourceKey);
            if (current == null) {
                continue;
            }
            log.debug("Attempting to load configuration from high-priority source: {}", sourceKey);
            CorsConfig config = current.load();
            if (config != null) {
                activeSource.set(current);
                log.info("Successfully loaded configuration from source: {}", sourceKey);
                return config;
            }
        }
        log.warn("All remote configuration sources failed. Falling back to local configuration.");
        activeSource.set(localSource);
        CorsConfig localConfig = localSource.load();
        return localConfig != null
                ? localConfig
                : new CorsConfig.Builder()
                .enabled(false)
                .allowOrigin("*")
                .allowMethods("*")
                .allowHeaders("*")
                .exposeHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
                .build();
    }

    @Override
    public void onChange(Runnable callback) {
        this.changeCallback = callback;
        for (ConfigSource source : remoteSources.values()) {
            source.onChange(changeCallback);
        }
    }

    private void startFailbackCheck(String sourceId, ConfigSource recoveredSource) {
        if (!(activeSource.get() instanceof LocalConfigSource)) {
            log.warn("Highest priority source {} recovered, but currently active source is {}. Auto failback is DISABLED to preserve stability.",
                    sourceId, activeSource.get().getClass().getSimpleName());
            return;
        }
        log.info("Highest priority source {} recovered from Local Fallback. Starting DELAYED failback check.", sourceId);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            CorsConfig configCheck = recoveredSource.load();
            if (configCheck != null) {
                activeSource.set(recoveredSource);
                recoveredSource.onChange(changeCallback);

                if (changeCallback != null) {
                    changeCallback.run();
                }
                log.info("Config Manager successfully switched active source to {}", sourceId);
            } else {
                log.warn("Source {} failed stablity check after recovery. Remaining on Local Fallback. ", sourceId);
            }
        }, 10, TimeUnit.SECONDS);
    }
}
