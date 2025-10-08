package com.yonagi.ocean.core.configuration;

import com.yonagi.ocean.core.Router;
import com.yonagi.ocean.core.configuration.source.route.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 17:03
 */
public class RouteConfigManager {

    private final Router router;
    private volatile Map<String, RouteConfig> routeMap = Collections.emptyMap();

    private static final Logger log = LoggerFactory.getLogger(RouteConfigManager.class);

    public RouteConfigManager(Router router) {
        this.router = router;
    }

    private static String generateKey(RouteConfig routeConfig) {
        return routeConfig.getMethod().name() + ":" + routeConfig.getPath();
    }

    public void refreshRoutes(ConfigSource configSource) {
        try {
            List<RouteConfig> routeConfigs = configSource.load();
            if (routeConfigs == null) {
                return;
            }

            Map<String, RouteConfig> newRouteMap = routeConfigs.stream()
                    .collect(Collectors.toMap(RouteConfigManager::generateKey, Function.identity()));
            Map<String, RouteConfig> oldRouteMap = this.routeMap;
            oldRouteMap.forEach((key, oldConfig) -> {
                if (!newRouteMap.containsKey(key)) {
                    router.unregisterRoute(oldConfig);
                    log.info("Unregistered route: {}", key);
                }
            });
            newRouteMap.forEach((key, newConfig) -> {
                RouteConfig oldConfig = oldRouteMap.get(key);
                if (oldConfig == null) {
                    router.registerRoute(newConfig);
                    log.info("Registered route: {}", key);
                } else if (!newConfig.equals(oldConfig)) {
                    router.updateRoute(oldConfig, newConfig);
                    log.info("Updated route: {}", key);
                }
            });
            this.routeMap = newRouteMap;
            log.info("Router refresh with {} custom routes", routeMap.size());

            Map<String, Object> routeStats = router.getRouteStats();
            log.info("Router refreshed - Total routes: {}, Handler cache size: {}",
                    routeStats.get("totalRoutes"), routeStats.get("handlerCacheSize"));
        } catch (Exception e) {
            log.error("Failed to refresh router: {}", e.getMessage(), e);
        }
    }

    public void initializeRoutes(ConfigSource configSource) {
        try {
            List<RouteConfig> routeConfigs = configSource.load();
            this.router.registerRoutes(routeConfigs);
            if (routeConfigs == null || routeConfigs.isEmpty()) {
                return;
            }
            this.routeMap = routeConfigs.stream()
                    .collect(Collectors.toMap(RouteConfigManager::generateKey, Function.identity()));

            Map<String, Object> stats = this.router.getRouteStats();
            log.info("Router initialized - Total routes: {}, Handler cache size: {}",
                    stats.get("totalRoutes"), stats.get("handlerCacheSize"));
        } catch (Exception e) {
            log.error("Failed to initialize router: {}", e.getMessage(), e);
        }
    }
}
