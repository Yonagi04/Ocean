package com.yonagi.ocean.core.router;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.router.config.RouteConfig;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.handler.impl.*;
import com.yonagi.ocean.admin.metrics.MetricsRegistry;
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
import java.util.stream.Collectors;

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

        public final List<String> pathSegments;

        public final List<String> pathVariableNames;

        public RouteEntry(RouteConfig config, RequestHandler handler) {
            this.config = config;
            this.handler = handler;

            if (config.getRouteType() == RouteType.CONTROLLER || (config.getPath().contains("{") && config.getPath().contains("}"))) {
                this.pathSegments = parsePathSegments(config.getPath());
                this.pathVariableNames = extractPathVariableNames(this.pathSegments);
            } else {
                this.pathSegments = Collections.emptyList();
                this.pathVariableNames = Collections.emptyList();
            }
        }

        public RouteEntry(RouteConfig config) {
            this(config, null);
        }

        public static List<String> parsePathSegments(String path) {
            return Arrays.stream(path.split("/"))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        public static List<String> extractPathVariableNames(List<String> segments) {
            return segments.stream()
                    .filter(s -> s.startsWith("{") && s.endsWith("}"))
                    .map(s -> s.substring(1, s.length() - 1))
                    .collect(Collectors.toList());
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
    private final Map<HttpMethod, List<RouteEntry>> staticRoutes = new ConcurrentHashMap<>();

    // 动态层路由
    private volatile Map<HttpMethod, Map<String, RouteEntry>> dynamicRoutes = new ConcurrentHashMap<>();

    // 动态层路径变量路由
    private final Map<HttpMethod, List<RouteEntry>> dynamicPathVariableRoutes = new ConcurrentHashMap<>();

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
        defaultHandlers.put(HttpMethod.POST, new ApiHandler());
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

        staticRoutes.computeIfAbsent(method, k -> Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(new RouteEntry(config, handler));
        log.debug("Registered static controller: {} {}", method, config.getPath());
    }

    /**
     * 注册动态路由(动态化配置专用)
     * 根据路径中是否有变量，注册到不同的结构
     */
    public void registerRoute(RouteConfig config) {
        if (config == null) {
            log.warn("Attempted to register a null route");
            return;
        } else if (config.getRouteType() == RouteType.CONTROLLER) {
            return;
        }
        HttpMethod method = config.getMethod();
        String path = config.getPath();
        RouteEntry entry = new RouteEntry(config);

        if (path.contains("{") && path.contains("}")) {
            dynamicPathVariableRoutes.computeIfAbsent(method, k -> Collections.synchronizedList(new java.util.ArrayList<>()))
                    .add(entry);
            log.debug("Registered dynamic path variable router: {} {} (Type: {})", method, path, config.getRouteType().name());
        } else {
            dynamicRoutes.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                    .put(path, entry);
            log.debug("Registered dynamic router: {} {} (Type: {})", method, path, config.getRouteType().name());
        }
    }

    /**
     * 注销路由配置(动态化配置)
     * 根据路径判断是从map注销还是从list注销
     */
    public void unregisterRoute(RouteConfig config) {
        HttpMethod method = config.getMethod();
        String path = config.getPath();

        if (path.contains("{") && path.contains("}")) {
            List<RouteEntry> methodRoutes = dynamicPathVariableRoutes.get(method);
            if (methodRoutes != null) {
                if (methodRoutes.removeIf(e -> e.config.equals(config))) {
                    log.debug("Unregistered dynamic path variable router: {} {}", method, path);
                }
            }
        } else {
            Map<String, RouteEntry> methodRoutes = dynamicRoutes.get(method);
            if (methodRoutes != null) {
                if (methodRoutes.remove(path) != null) {
                    log.debug("Unregistered dynamic router: {} {}", method, path);
                }
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
    public void route(HttpContext httpContext) throws IOException {
        HttpMethod method = httpContext.getRequest().getMethod();
        String path = httpContext.getRequest().getUri();
        HttpRequest request = httpContext.getRequest();
        OutputStream output = httpContext.getOutput();
        boolean keepAlive = httpContext.isKeepalive();

        RouteEntry entry = null;

        // 查找稳定路由（Controller），支持通配符
        entry = findPathMatchInControllerRoutes(staticRoutes, method, path, request);

        // 查找动态路由，支持路径变量
        if (entry == null) {
            entry = findPathMatchInControllerRoutes(dynamicPathVariableRoutes, method, path, request);
        }

        // 查找简单动态路由
        if (entry == null) {
            entry = findRouteEntry(dynamicRoutes, method, path);
        }

        if (entry != null) {
            if (handleRouteEntry(entry, httpContext)) {
                return;
            }
            MetricsRegistry metricsRegistry = httpContext.getConnectionContext().getServerContext().getMetricsRegistry();
            metricsRegistry.getRouteFallbackCounter().increment();
            log.warn("[{}] Custom router failed, falling back to default handler for {} {}", httpContext.getTraceId(), method, path);
        }
        RequestHandler defaultHandler = defaultHandlers.get(method);
        if (defaultHandler != null) {
            defaultHandler.handle(httpContext);
        } else {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.METHOD_NOT_ALLOWED)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body("HTTP Method not specified or supported.".getBytes())
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
        }
    }

    /**
     * 查找匹配的 Controller 路由 或 普通路径变量路由
     */
    private RouteEntry findPathMatchInControllerRoutes(Map<HttpMethod, List<RouteEntry>> routes,
                                                       HttpMethod method, String requestPath, HttpRequest request) {

        List<RouteEntry> specificMethodRoutes = routes.get(method);
        if (specificMethodRoutes == null) {
            return null;
        }

        List<String> requestSegments = RouteEntry.parsePathSegments(requestPath);

        for (RouteEntry entry : specificMethodRoutes) {
            if (requestSegments.size() != entry.pathSegments.size()) {
                continue;
            }

            Map<String, String> pathVariables = new HashMap<>();
            boolean match = true;

            for (int i = 0; i < requestSegments.size(); i++) {
                String patternSegment = entry.pathSegments.get(i);
                String requestSegment = requestSegments.get(i);

                if (patternSegment.startsWith("{") && patternSegment.endsWith("}")) {
                    String varName = patternSegment.substring(1, patternSegment.length() - 1);
                    pathVariables.put(varName, requestSegment);
                } else if (!patternSegment.equals(requestSegment)) {
                    match = false;
                    break;
                }
            }

            if (match) {
                request.getAttribute().setPathVariableAttributes(pathVariables);
                log.debug("Found Controller path match for: {} {} (Pattern: {})", method, requestPath, entry.config.getPath());
                return entry;
            }
        }
        return null;
    }

    /**
     * 查找匹配的路由配置，适用于简单路由
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
    private boolean handleRouteEntry(RouteEntry entry, HttpContext httpContext) throws IOException {
        RouteConfig routeConfig = entry.config;
        RouteType routeType = routeConfig.getRouteType();

        // 优先处理稳定路由
        if (routeType == RouteType.CONTROLLER && entry.handler != null) {
            log.debug("Handling request with Controller instance: {} -> {}",
                    httpContext.getRequest().getUri(), routeConfig.getHandlerClassName());
            entry.handler.handle(httpContext);
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
            HttpRequest request = httpContext.getRequest();
            log.debug("Handling request with dynamic router: {} -> {}", request.getUri(), handlerClassName);
            if (routeType == RouteType.REDIRECT) {
                // 对于重定向，传递目标URL和状态码
                request.getAttribute().setTargetUrl(routeConfig.getTargetUrl());
                request.getAttribute().setRedirectStatusCode(routeConfig.getStatusCode() == null ? 302 : routeConfig.getStatusCode());
                request.getAttribute().setRedirectContentType(routeConfig.getContentType());
                httpContext.setRequest(request);
            }
            handler.handle(httpContext);
            return true;
        } else {
            log.warn("[{}] Failed to create handler for router: {} - handler class not found or invalid", httpContext.getTraceId(), routeConfig);
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

        int staticControllerCount = staticRoutes.values().stream().mapToInt(List::size).sum();
        int dynamicSimpleCount = dynamicRoutes.values().stream().mapToInt(Map::size).sum();
        int dynamicPathVariableCount = dynamicPathVariableRoutes.values().stream().mapToInt(List::size).sum();
        int totalDynamic = dynamicSimpleCount + dynamicPathVariableCount;

        stats.put("totalRoutes", staticControllerCount + totalDynamic);
        stats.put("staticControllerRoutes", staticControllerCount);
        stats.put("dynamicSimpleRoutes", dynamicSimpleCount);
        stats.put("dynamicPathVariableRoutes", dynamicPathVariableCount);
        stats.put("totalDynamicRoutes", totalDynamic);
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
