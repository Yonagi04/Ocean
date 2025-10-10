package com.yonagi.ocean.core.configuration.source.route;

import com.alibaba.nacos.api.config.listener.Listener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yonagi.ocean.core.configuration.RouteConfig;
import com.yonagi.ocean.core.router.RouteType;
import com.yonagi.ocean.handler.impl.RedirectHandler;
import com.yonagi.ocean.handler.impl.StaticFileHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosBackupScheduler;
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
 * @date 2025/10/08 13:54
 */
public class NacosConfigSource implements ConfigSource {

    private Runnable callback;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(NacosConfigSource.class);
    private static final String STATIC_HANDLER_CLASS = StaticFileHandler.class.getName();
    private static final String REDIRECT_HANDLER_CLASS = RedirectHandler.class.getName();

    public NacosConfigSource() {
        NacosConfigLoader.init();
        startPeriodicSync();
    }

    @Override
    public List<RouteConfig> load() {
        try {
            String dataId = LocalConfigLoader.getProperty("server.router.nacos.data_id");
            String group = LocalConfigLoader.getProperty("server.router.nacos.group");
            int timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
            ArrayNode jsonArrayConfig = NacosConfigLoader.getJsonArrayConfig(dataId, group, timeoutMs);
            if (jsonArrayConfig == null) {
                return null;
            }
            TypeReference<List<RouteConfig>> typeRef = new TypeReference<List<RouteConfig>>() {
            };
            List<RouteConfig> routeConfigs = objectMapper.convertValue(jsonArrayConfig, typeRef);
            log.info("Loaded {} route configurations from Nacos configuration", routeConfigs.size());
            for (RouteConfig config : routeConfigs) {
                if (config.getRouteType() == RouteType.STATIC) {
                    RouteConfig.Builder builder = config.toBuilder();
                    builder.withHandlerClassName(STATIC_HANDLER_CLASS);
                    config = builder.build();
                } else if (config.getRouteType() == RouteType.REDIRECT) {
                    RouteConfig.Builder builder = config.toBuilder();
                    builder.withHandlerClassName(REDIRECT_HANDLER_CLASS);
                    config = builder.build();
                }
                log.info("Route: {} {} -> {} (enabled: {})",
                        config.getMethod(), config.getPath(), config.getHandlerClassName(), config.isEnabled());
            }
            return routeConfigs;
        } catch (Exception e) {
            log.error("Failed to load route configuration from Nacos: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        String dataId = LocalConfigLoader.getProperty("server.router.nacos.data_id");
        String group = LocalConfigLoader.getProperty("server.router.nacos.group");
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
        boolean enabledSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.router.nacos.sync_to_local", "true"));
        if (!enabledSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.router.nacos.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.router.nacos.sync_local_path");
        String dataId = LocalConfigLoader.getProperty("server.router.nacos.data_id");
        String group = LocalConfigLoader.getProperty("server.router.nacos.group");
        int timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "5000"));
        NacosBackupScheduler.start(dataId, group, syncLocalPath, syncIntervalSeconds, timeoutMs);
    }
}
