package com.yonagi.ocean.core.router.config.source;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.core.router.config.RouteConfig;
import com.yonagi.ocean.enums.RemoteSource;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/15 19:38
 */
public class ConfigManager implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    private final List<RemoteSource> prioritySources;

    private final Map<RemoteSource, ConfigSource> remoteSources;

    private final LocalConfigSource localSource;

    private final AtomicReference<ConfigSource> activeSource = new AtomicReference<>();

    private Runnable callback;

    public ConfigManager(ConfigService nacosConfigService) {
        String priorityStr = LocalConfigLoader.getProperty("server.router.remote_sources.priority", "nacos,apollo");
        this.prioritySources = List.of(priorityStr.split(",")).stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .map(name -> RemoteSource.getAllRemoteSources().stream()
                        .filter(remoteSource -> remoteSource.getName().equals(name))
                        .findFirst()
                        .orElse(null))
                .toList();
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
            NacosConfigLoader.registerRecoveryAction(configService ->
                    startFailbackCheck(RemoteSource.NACOS.name(), new NacosConfigSource(nacosConfigService)));
        }
        this.load();
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        for (ConfigSource configSource : remoteSources.values()) {
            configSource.onChange(callback);
        }
    }

    @Override
    public List<RouteConfig> load() {
        for (RemoteSource remoteSource : prioritySources) {
            ConfigSource configSource = remoteSources.get(remoteSource);
            if (configSource == null) {
                continue;
            }
            log.debug("Attempting to load configuration from high-priority source: {}", remoteSource);
            List<RouteConfig> config = configSource.load();
            if (config != null) {
                activeSource.set(configSource);
                log.info("Configuration successfully loaded from primary source: {}", remoteSource);
                return config;
            }
        }
        log.warn("All remote configuration sources failed. Falling back to Local configuration.");
        activeSource.set(localSource);
        List<RouteConfig> localConfig = localSource.load();
        return localConfig != null
                ? localConfig
                : new ArrayList<>();
    }

    private void startFailbackCheck(String sourceId, ConfigSource configSource) {
        if (!(activeSource.get() instanceof LocalConfigSource)) {
            log.warn("Highest priority source {} recovered, but currently active source is {}. Auto failback is DISABLED to preserve stability.",
                    sourceId, activeSource.get().getClass().getSimpleName());
            return;
        }
        log.info("Highest priority source {} recovered from Local Fallback. Starting DELAYED failback check.", sourceId);
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            List<RouteConfig> configCheck = configSource.load();
            if (configCheck != null) {
                activeSource.set(configSource);
                configSource.onChange(callback);

                if (callback != null) {
                    callback.run();
                }
                log.info("Config Manager successfully switched active source to {}", sourceId);
            } else {
                log.warn("Source {} failed stablity check after recovery. Remaining on Local Fallback. ", sourceId);
            }
        }, 10, TimeUnit.SECONDS);
    }
}
