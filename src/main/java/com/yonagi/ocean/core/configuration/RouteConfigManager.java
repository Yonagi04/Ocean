package com.yonagi.ocean.core.configuration;

import com.yonagi.ocean.core.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    public void refreshRoutes(List<RouteConfig> newRoutes) {
        if (newRoutes == null) {
            return;
        }
        Map<String, RouteConfig> newRouteMap = newRoutes.stream()
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
    }

    public void initializeRoutes(List<RouteConfig> currentRoutes) {
        if (currentRoutes == null) {
            return;
        }
        this.routeMap = currentRoutes.stream()
                .collect(Collectors.toMap(RouteConfigManager::generateKey, Function.identity()));
    }
}
