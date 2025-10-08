package com.yonagi.ocean.core;

import com.yonagi.ocean.config.RouteConfig;
import com.yonagi.ocean.core.protocol.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.handler.impl.*;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    /**
     * 缓存条目，包含值和过期时间
     */
    private static class CacheEntry {
        private final RequestHandler handler;
        private final long expireTime;
        private long lastAccessTime;
        
        public CacheEntry(RequestHandler handler, long ttlMs) {
            this.handler = handler;
            this.expireTime = System.currentTimeMillis() + ttlMs;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public RequestHandler getHandler() {
            this.lastAccessTime = System.currentTimeMillis();
            return handler;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }
    
    /**
     * LRU缓存实现，支持TTL过期
     */
    private static class LRUCache<K, V> {
        private final int maxSize;
        private final long ttlMs;
        private final Map<K, CacheEntry> cache;
        private final LinkedHashMap<K, Long> accessOrder;
        
        public LRUCache(int maxSize, long ttlMs) {
            this.maxSize = maxSize;
            this.ttlMs = ttlMs;
            this.cache = new ConcurrentHashMap<>();
            this.accessOrder = new LinkedHashMap<K, Long>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, Long> eldest) {
                    return size() > maxSize;
                }
            };
        }
        
        public synchronized V get(K key) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            
            if (entry.isExpired()) {
                cache.remove(key);
                accessOrder.remove(key);
                return null;
            }
            
            // 更新访问顺序
            accessOrder.put(key, entry.getLastAccessTime());
            return (V) entry.getHandler();
        }
        
        public synchronized V put(K key, V value) {
            CacheEntry oldEntry = cache.get(key);
            
            // 创建新的缓存条目
            CacheEntry newEntry = new CacheEntry((RequestHandler) value, ttlMs);
            cache.put(key, newEntry);
            accessOrder.put(key, newEntry.getLastAccessTime());
            
            // 如果超过最大大小，移除最旧的条目
            while (accessOrder.size() > maxSize) {
                Map.Entry<K, Long> eldest = accessOrder.entrySet().iterator().next();
                K eldestKey = eldest.getKey();
                cache.remove(eldestKey);
                accessOrder.remove(eldestKey);
            }
            
            return oldEntry != null ? (V) oldEntry.getHandler() : null;
        }
        
        public synchronized V remove(K key) {
            CacheEntry entry = cache.remove(key);
            accessOrder.remove(key);
            return entry != null ? (V) entry.getHandler() : null;
        }
        
        public synchronized int size() {
            return cache.size();
        }
        
        public synchronized void clear() {
            cache.clear();
            accessOrder.clear();
        }
        
        /**
         * 清理过期的条目
         */
        public synchronized int cleanupExpired() {
            List<K> expiredKeys = new ArrayList<>();
            long now = System.currentTimeMillis();
            
            for (Map.Entry<K, CacheEntry> entry : cache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    expiredKeys.add(entry.getKey());
                }
            }
            
            for (K key : expiredKeys) {
                cache.remove(key);
                accessOrder.remove(key);
            }
            
            return expiredKeys.size();
        }
    }
    
    private static final Logger log = LoggerFactory.getLogger(Router.class);
    
    // 路由映射表: method -> path -> RouteConfig
    private final Map<HttpMethod, Map<String, RouteConfig>> routes = new ConcurrentHashMap<>();
    
    // 处理器实例缓存: class name -> handler instance (使用LRU+TTL缓存)
    private final LRUCache<String, RequestHandler> handlerCache;
    
    // 默认处理器映射
    private final Map<HttpMethod, RequestHandler> defaultHandlers = new ConcurrentHashMap<>();
    
    private final String webRoot;
    
    // 缓存清理调度器
    private final ScheduledExecutorService cleanupScheduler;
    
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
    
    /**
     * 初始化默认处理器
     */
    private void initDefaultHandlers() {
        defaultHandlers.put(HttpMethod.GET, new StaticFileHandler(webRoot));
        defaultHandlers.put(HttpMethod.POST, new ApiHandler(webRoot));
        defaultHandlers.put(HttpMethod.HEAD, new HeadHandler(webRoot));
        defaultHandlers.put(HttpMethod.OPTIONS, new OptionsHandler(webRoot));
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
     * 注册单个路由
     */
    private void registerRoute(RouteConfig config) {
        HttpMethod method = config.getMethod();
        String path = config.getPath();
        
        routes.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
              .put(path, config);
        
        log.debug("Registered route: {} {}", method, path);
    }
    
    /**
     * 路由匹配和请求处理
     */
    public void route(HttpMethod method, String path, HttpRequest request,
                     java.io.OutputStream output, boolean keepAlive) throws java.io.IOException {
        // 首先尝试精确匹配自定义路由
        RouteConfig routeConfig = findRoute(method, path);
        if (routeConfig != null) {
            if (handleCustomRoute(routeConfig, request, output, keepAlive)) {
                return;
            }
            log.warn("Custom route failed, falling back to default handler for {} {}", method, path);
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
    private RouteConfig findRoute(HttpMethod method, String path) {
        Map<String, RouteConfig> methodRoutes = routes.get(method);
        if (methodRoutes == null) {
            return null;
        }
        
        // 精确匹配
        RouteConfig exactMatch = methodRoutes.get(path);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // TODO: 可以在这里添加路径参数匹配逻辑，如 /api/users/{id}
        // 目前只支持精确匹配
        
        return null;
    }
    
    /**
     * 处理自定义路由
     * @return true 如果处理成功，false 如果处理失败需要回退到默认处理器
     */
    private boolean handleCustomRoute(RouteConfig routeConfig, com.yonagi.ocean.core.protocol.HttpRequest request, 
                                    java.io.OutputStream output, boolean keepAlive) throws java.io.IOException {
        
        String handlerClassName = routeConfig.getHandlerClassName();
        RequestHandler handler = getOrCreateHandler(handlerClassName);
        
        if (handler != null) {
            log.debug("Handling request with custom route: {} -> {}", request.getUri(), handlerClassName);
            handler.handle(request, output, keepAlive);
            return true;
        } else {
            log.warn("Failed to create handler for route: {} - handler class not found or invalid", routeConfig);
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
        
        int totalRoutes = 0;
        Map<String, Integer> methodStats = new HashMap<>();
        
        for (Map.Entry<HttpMethod, Map<String, RouteConfig>> entry : routes.entrySet()) {
            HttpMethod method = entry.getKey();
            Map<String, RouteConfig> methodRoutes = entry.getValue();
            int methodRouteCount = methodRoutes.size();
            
            methodStats.put(method.toString(), methodRouteCount);
            totalRoutes += methodRouteCount;
        }
        
        stats.put("totalRoutes", totalRoutes);
        stats.put("methodStats", methodStats);
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
