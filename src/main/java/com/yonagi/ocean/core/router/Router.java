package com.yonagi.ocean.core.router;

import com.alibaba.nacos.api.remote.request.Request;
import com.yonagi.ocean.core.configuration.RouteConfig;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.handler.impl.*;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description 路由器，负责路由注册和请求转发
 * @date 2025/10/08 11:12
 */
public class Router {
    private static final Logger log = LoggerFactory.getLogger(Router.class);

    public static class RouteEntry {
        public final RouteConfig config;

        public final RequestHandler handler;

        public RouteEntry(RouteConfig config, RequestHandler handler) {
            this.config = config;
            this.handler = handler;
        }

        public RouteEntry(RouteConfig config) {
            this(config, null);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            RouteEntry that = (RouteEntry) obj;
            return Objects.equals(config, that.config);
        }
    }

    // 稳定层路由
    private final Map<HttpMethod, Map<String, RouteEntry>> staticRoutes = new ConcurrentHashMap<>();

    // 动态层路由
    private volatile Map<HttpMethod, Map<String, RouteEntry>> dynamicRoutes = new ConcurrentHashMap<>();

    // 处理器实例缓存: class name -> handler instance (使用LRU+TTL缓存)
    private final LRUCache<String, RequestHandler> handlerCache;
    
    // 默认处理器映射
    private final Map<HttpMethod, RequestHandler> defaultHandlers = new ConcurrentHashMap<>();
    
    private final String webRoot;
    
    // 缓存清理调度器
    private final ScheduledExecutorService cleanupScheduler;

    private static final String STATIC_HANDLER_CLASS = StaticFileHandler.class.getName();

    private static final String REDIRECT_HANDLER_CLASS = RedirectHandler.class.getName();
    
    public Router(String webRoot) {
        this.webRoot = webRoot;
        
        // 从配置中读取缓存参数
        int cacheSize = Math.max(Integer.parseInt(LocalConfigLoader.getProperty("server.router.cache_size", "100")), 100);
        long cacheTtlSeconds = Long.parseLong(LocalConfigLoader.getProperty("server.router.cache_ttl_seconds", "3600"));
        long cacheTtlMs = cacheTtlSeconds * 1000L;
        
        // 初始化LRU缓存
        this.handlerCache = new LRUCache<>(cacheSize, cacheTtlMs);
        
        // 初始化缓存清理调度器
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Router-Cache-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // 启动定期清理任务 (每30秒清理一次过期条目)
        long cleanupIntervalSeconds = Long.parseLong(LocalConfigLoader.getProperty("server.router.cache_cleanup_interval_seconds", "30"));
        this.cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredCache, 
                cleanupIntervalSeconds, cleanupIntervalSeconds, TimeUnit.SECONDS);
        
        initDefaultHandlers();
        
        log.info("Router initialized with LRU cache: size={}, ttl={}s, cleanup_interval={}s", 
                cacheSize, cacheTtlSeconds, cleanupIntervalSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.staticRoutes, this.dynamicRoutes, this.handlerCache, this.defaultHandlers, this.webRoot);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Router that = (Router) obj;
        return Objects.equals(this.webRoot, that.webRoot) &&
               Objects.equals(this.staticRoutes, that.staticRoutes) &&
               Objects.equals(this.dynamicRoutes, that.dynamicRoutes) &&
               Objects.equals(this.handlerCache, that.handlerCache) &&
               Objects.equals(this.defaultHandlers, that.defaultHandlers);
    }

    /**
     * 初始化默认处理器
     */
    private void initDefaultHandlers() {
        defaultHandlers.put(HttpMethod.GET, new StaticFileHandler(webRoot));
        defaultHandlers.put(HttpMethod.POST, new ApiHandler(webRoot));
        defaultHandlers.put(HttpMethod.HEAD, new HeadHandler(webRoot));
    }
    
    /**
     * 注册路由配置
     */
    public void registerRoutes(List<RouteConfig> routeConfigs) {
        for (RouteConfig config : routeConfigs) {
            if (config.isEnabled()) {
                registerRoute(config);
            }
        }
        log.info("Router initialized with {} custom routes", routeConfigs.size());
    }

    /**
     * 注册稳定路由（Controller注入专用）
     */
    public void registerRoute(RouteConfig config, RequestHandler handler) {
        if (config == null || config.getRouteType() != RouteType.CONTROLLER) {
            log.warn("Attempted to register non-Controller route with instance method: {}", config);
            return;
        }
        HttpMethod method = config.getMethod();
        String path = config.getPath();
        RouteEntry entry = new RouteEntry(config, handler);

        staticRoutes.computeIfAbsent(method, k -> new ConcurrentHashMap<>()).put(path, entry);
        log.debug("Registered static controller: {} {} (Type: {})", method, path, config.getRouteType().name());
    }

    /**
     * 注册单个路由(动态化配置专用)
     */
    public void registerRoute(RouteConfig config) {
        if (config == null || config.getRouteType() == RouteType.CONTROLLER) {
            return;
        }
        HttpMethod method = config.getMethod();
        String path = config.getPath();
        RouteEntry entry = new RouteEntry(config);

        dynamicRoutes.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
              .put(path, entry);
        
        log.debug("Registered dynamic router: {} {} (Type: {})", method, path, config.getRouteType().name());
    }

    /**
     * 注销路由配置(动态化配置)
     */
    public void unregisterRoute(RouteConfig config) {
        HttpMethod method = config.getMethod();
        String path = config.getPath();

        Map<String, RouteEntry> methodRoutes = dynamicRoutes.get(method);
        if (methodRoutes != null) {
            if (methodRoutes.remove(path) != null) {
                log.debug("Unregistered dynamic router: {} {}", method, path);
            }
        }
    }

    /**
     * 更新路由配置(动态化配置)
     */
    public void updateRoute(RouteConfig oldConfig, RouteConfig newConfig) {
        unregisterRoute(oldConfig);
        registerRoute(newConfig);
        log.debug("Updated dynamic router: {} {}", newConfig.getMethod(), newConfig.getPath());
    }
    
    /**
     * 路由匹配和请求处理
     */
    public void route(HttpMethod method, String path, HttpRequest request,
                     OutputStream output, boolean keepAlive) throws IOException {
        RouteEntry entry = null;

        // 查找稳定路由（Controller）
        entry = findRouteEntry(staticRoutes, method, path);
        // 如果稳定路由未命中，则查找动态路由
        if (entry == null) {
            entry = findRouteEntry(dynamicRoutes, method, path);
        }

        if (entry != null) {
            if (handleRouteEntry(entry, request, output, keepAlive)) {
                return;
            }
            log.warn("Custom router failed, falling back to default handler for {} {}", method, path);
        }
        RequestHandler defaultHandler = defaultHandlers.get(method);
        if (defaultHandler != null) {
            defaultHandler.handle(request, output, keepAlive);
        } else {
            new MethodNotAllowHandler().handle(request, output, keepAlive);
        }
    }

    /**
     * 查找匹配的路由配置
     */
    private RouteEntry findRouteEntry(Map<HttpMethod, Map<String, RouteEntry>> routes, HttpMethod method, String path) {
        RouteEntry match = null;

        Map<String, RouteEntry> specificMethodRoutes = routes.get(method);
        if (specificMethodRoutes != null) {
            match = findMatchInRoutes(specificMethodRoutes, path);
            if (match != null) {
                return match;
            }
        }

        Map<String, RouteEntry> allMethodRoutes = routes.get(HttpMethod.ALL);
        if (allMethodRoutes != null) {
            match = findMatchInRoutes(allMethodRoutes, path);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private RouteEntry findMatchInRoutes(Map<String, RouteEntry> methodRoutes, String path) {
        RouteEntry exactMatch = methodRoutes.get(path);
        if (exactMatch != null) {
            return exactMatch;
        }
        for (Map.Entry<String, RouteEntry> entry : methodRoutes.entrySet()) {
            String registeredPath = entry.getKey();

            if (registeredPath.endsWith("*")) {
                String prefix = registeredPath.substring(0, registeredPath.length() - 1);
                if (path.startsWith(prefix)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 处理自定义路由
     * @return true 如果处理成功，false 如果处理失败需要回退到默认处理器
     */
    private boolean handleRouteEntry(RouteEntry entry, HttpRequest request,
                                     OutputStream output, boolean keepAlive) throws IOException {
        RouteConfig routeConfig = entry.config;
        RouteType routeType = routeConfig.getRouteType();

        // 优先处理稳定路由
        if (routeType == RouteType.CONTROLLER && entry.handler != null) {
            log.debug("Handling request with Controller instance: {} -> {}",
                    request.getUri(), routeConfig.getHandlerClassName());
            entry.handler.handle(request, output, keepAlive);
            return true;
        }

        // 处理动态路由
        RequestHandler handler = null;
        String handlerClassName = "";
        if (routeType == RouteType.HANDLER) {
            handlerClassName = routeConfig.getHandlerClassName();
        } else if (routeType == RouteType.STATIC) {
            handlerClassName = STATIC_HANDLER_CLASS;
        } else if (routeType == RouteType.REDIRECT) {
            handlerClassName = REDIRECT_HANDLER_CLASS;
        }
        if (!handlerClassName.isEmpty()) {
            handler = getOrCreateHandler(handlerClassName);
        }

        if (handler != null) {
            log.debug("Handling request with dynamic router: {} -> {}", request.getUri(), handlerClassName);
            if (routeType == RouteType.REDIRECT) {
                // 对于重定向，传递目标URL和状态码
                request.setAttribute("targetUrl", routeConfig.getTargetUrl());
                request.setAttribute("statusCode", routeConfig.getStatusCode() == null ? 302 : routeConfig.getStatusCode());
                request.setAttribute("contentType", routeConfig.getContentType());
            }
            handler.handle(request, output, keepAlive);
            return true;
        } else {
            log.warn("Failed to create handler for router: {} - handler class not found or invalid", routeConfig);
            return false;
        }
    }
    
    /**
     * 获取或创建处理器实例
     */
    private RequestHandler getOrCreateHandler(String handlerClassName) {
        // 先从缓存中获取
        RequestHandler cachedHandler = handlerCache.get(handlerClassName);
        if (cachedHandler != null) {
            return cachedHandler;
        }
        
        // 缓存未命中，创建新的处理器实例
        try {
            Class<?> handlerClass = Class.forName(handlerClassName);
            if (RequestHandler.class.isAssignableFrom(handlerClass)) {
                RequestHandler handler;
                // 尝试使用webRoot参数构造
                try {
                    handler = (RequestHandler) handlerClass.getConstructor(String.class).newInstance(webRoot);
                } catch (Exception e) {
                    handler = (RequestHandler) handlerClass.getDeclaredConstructor().newInstance();
                }
                
                // 将新创建的处理器放入缓存
                handlerCache.put(handlerClassName, handler);
                log.debug("Created and cached new handler: {}", handlerClassName);
                return handler;
            } else {
                log.warn("Handler class {} does not implement RequestHandler interface", handlerClassName);
                return null;
            }
        } catch (ClassNotFoundException e) {
            log.warn("Handler class not found: {}", handlerClassName);
            return null;
        } catch (Exception e) {
            log.warn("Failed to create handler instance for class {}: {}", handlerClassName, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取路由统计信息
     */
    public Map<String, Object> getRouteStats() {
        Map<String, Object> stats = new HashMap<>();
        
        int staticCount = staticRoutes.values().stream().mapToInt(Map::size).sum();
        int dynamicCount = dynamicRoutes.values().stream().mapToInt(Map::size).sum();
        
        stats.put("totalRoutes", staticCount + dynamicCount);
        stats.put("staticRoutes", staticCount);
        stats.put("dynamicRoutes", dynamicCount);
        stats.put("handlerCacheSize", handlerCache.size());
        
        return stats;
    }
    
    /**
     * 清除路由缓存
     */
    public void clearCache() {
        handlerCache.clear();
        log.info("Router cache cleared");
    }
    
    /**
     * 清理过期的缓存条目
     */
    private void cleanupExpiredCache() {
        try {
            int expiredCount = handlerCache.cleanupExpired();
            if (expiredCount > 0) {
                log.debug("Cleaned up {} expired cache entries", expiredCount);
            }
        } catch (Exception e) {
            log.warn("Error during cache cleanup: {}", e.getMessage());
        }
    }
    
    /**
     * 关闭路由器，清理资源
     */
    public void shutdown() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        handlerCache.clear();
        log.info("Router shutdown completed");
    }
}
