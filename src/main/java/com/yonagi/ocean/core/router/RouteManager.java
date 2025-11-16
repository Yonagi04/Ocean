package com.yonagi.ocean.core.router;

import com.yonagi.ocean.core.router.config.RouteConfig;
import com.yonagi.ocean.core.router.config.source.ConfigManager;
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
public class RouteManager {

    private static volatile RouteManager INSTANCE;
    private final Router router;
    private volatile Map<String, RouteConfig> dynamicRouteMap = Collections.emptyMap();

    private static final Logger log = LoggerFactory.getLogger(RouteManager.class);

    public RouteManager(Router router) {
        this.router = router;
    }

    public static RouteManager getInstance() {
        return INSTANCE;
    }

    public static void setInstance(RouteManager manager) {
        INSTANCE = manager;
    }

    private static String generateKey(RouteConfig routeConfig) {
        return routeConfig.getMethod().name() + ":" + routeConfig.getPath();
    }

    public void refreshRoutes(ConfigManager configManager) {
        try {
            List<RouteConfig> newConfigs = configManager.load();
            List<RouteConfig> oldConfigs = configManager.getCurrentConfigSnapshot().get();
            if (oldConfigs != null && newConfigs.equals(oldConfigs)) {
                log.debug("Configuration source changed, but final merged configuration remains the same.");
                return;
            }
            if (newConfigs == null) {
                return;
            }
            configManager.getCurrentConfigSnapshot().set(newConfigs);
            List<RouteConfig> filteredConfigs = newConfigs.stream()
                    .filter(c -> c.getRouteType() != RouteType.CONTROLLER)
                    .collect(Collectors.toList());

            Map<String, RouteConfig> newRouteMap = filteredConfigs.stream()
                    .collect(Collectors.toMap(RouteManager::generateKey, Function.identity(), (e1, e2) -> e2));
            Map<String, RouteConfig> oldRouteMap = this.dynamicRouteMap;

            oldRouteMap.forEach((key, oldConfig) -> {
                if (!newRouteMap.containsKey(key)) {
                    router.unregisterRoute(oldConfig);
                    log.info("Unregistered dynamic router: {}", key);
                }
            });
            newRouteMap.forEach((key, newConfig) -> {
                if (!newConfig.isEnabled()) {
                    router.unregisterRoute(newConfig);
                    log.info("Disabled dynamic router: {}", key);
                    return;
                }
                RouteConfig oldConfig = oldRouteMap.get(key);
                if (oldConfig == null) {
                    router.registerRoute(newConfig);
                    log.info("Registered dynamic router: {}", key);
                } else if (!newConfig.equals(oldConfig)) {
                    router.updateRoute(oldConfig, newConfig);
                    log.info("Updated dynamic router: {}", key);
                }
            });
            this.dynamicRouteMap = newRouteMap;
            log.info("Dynamic Router refresh with {} custom routes", dynamicRouteMap.size());

            Map<String, Object> routeStats = router.getRouteStats();
            log.info("Router refreshed - Total routes: {}, Static routes: {}, " +
                            "Dynamic simple routes: {}, Dynamic path variable routes: {}, Total dynamic routes: {}, Handler cache size: {}",
                    routeStats.get("totalRoutes"), routeStats.get("staticControllerRoutes"),
                    routeStats.get("dynamicSimpleRoutes"), routeStats.get("dynamicPathVariableRoutes"),
                    routeStats.get("totalDynamicRoutes"), routeStats.get("handlerCacheSize"));
        } catch (Exception e) {
            log.error("Failed to refresh router: {}", e.getMessage(), e);
        }
    }

    public void initializeRoutes(ConfigManager configManager) {
        try {
            List<RouteConfig> routeConfigs = configManager.load();
            if (routeConfigs == null || routeConfigs.isEmpty()) {
                return;
            }

            List<RouteConfig> filteredRouteConfigs = routeConfigs.stream()
                    .filter(c -> c.getRouteType() != RouteType.CONTROLLER)
                    .collect(Collectors.toList());
            filteredRouteConfigs.forEach(router::registerRoute);

            this.dynamicRouteMap = filteredRouteConfigs.stream()
                    .collect(Collectors.toMap(RouteManager::generateKey, Function.identity(), (e1, e2) -> e2));

            Map<String, Object> routeStats = this.router.getRouteStats();
            log.info("Router refreshed - Total routes: {}, Static routes: {}, " +
                            "Dynamic simple routes: {}, Dynamic path variable routes: {}, Total dynamic routes: {}, Handler cache size: {}",
                    routeStats.get("totalRoutes"), routeStats.get("staticControllerRoutes"),
                    routeStats.get("dynamicSimpleRoutes"), routeStats.get("dynamicPathVariableRoutes"),
                    routeStats.get("totalDynamicRoutes"), routeStats.get("handlerCacheSize"));
        } catch (Exception e) {
            log.error("Failed to initialize router: {}", e.getMessage(), e);
        }
    }
}
