package com.yonagi.ocean.core;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.configuration.KeepAliveConfig;
import com.yonagi.ocean.core.configuration.RouteConfig;
import com.yonagi.ocean.core.configuration.RouteConfigManager;
import com.yonagi.ocean.core.configuration.source.route.ConfigSource;
import com.yonagi.ocean.core.configuration.source.route.FallbackConfigSource;
import com.yonagi.ocean.core.configuration.source.route.LocalConfigSource;
import com.yonagi.ocean.core.configuration.source.route.NacosConfigSource;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/03 17:33
 */
public class HttpServer {

    private Integer port;
    private String webRoot;
    private Boolean isRunning;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor threadPool;
    private KeepAliveConfig keepAliveConfig;
    private ConnectionManager connectionManager;
    private RouteConfig routeConfig;
    private Router router;
    private RouteConfigManager routeConfigManager;
    private ConfigSource configSource;

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    public HttpServer() {
        log.info("\n" +
                " _______  _______  _______  _______  _       \n" +
                "(  ___  )(  ____ \\(  ____ \\(  ___  )( (    /|\n" +
                "| (   ) || (    \\/| (    \\/| (   ) ||  \\  ( |\n" +
                "| |   | || |      | (__    | (___) ||   \\ | |\n" +
                "| |   | || |      |  __)   |  ___  || (\\ \\) |\n" +
                "| |   | || |      | (      | (   ) || | \\   |\n" +
                "| (___) || (____/\\| (____/\\| )   ( || )  \\  |\n" +
                "(_______)(_______/(_______/|/     \\||/    )_)\n" +
                "                                             ");
        int port = Integer.parseInt(LocalConfigLoader.getProperty("server.port"));
        String webRoot = LocalConfigLoader.getProperty("server.webroot");
        int corePoolSize = Math.max(Runtime.getRuntime().availableProcessors(),
                LocalConfigLoader.getProperty("server.thread_pool.core_size") == null ? 2 : Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.core_size")));
        int maximumPoolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1,
                LocalConfigLoader.getProperty("server.thread_pool.max_size") == null ? 4 : Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.max_size")));
        long keepAliveTime = Math.max(60L,
                LocalConfigLoader.getProperty("server.thread_pool.keep_alive_seconds") == null ? 60L : Long.parseLong(LocalConfigLoader.getProperty("server.thread_pool.keep_alive_seconds")));
        int queueCapacity = Math.max(1000,
                LocalConfigLoader.getProperty("server.thread_pool.queue_capacity") == null ? 1000 : Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.queue_capacity")));
        this.port = port;
        this.threadPool = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.webRoot = webRoot;
        
        // Initialize Keep-Alive configuration
        this.keepAliveConfig = new KeepAliveConfig.Builder()
                .enabled(Boolean.parseBoolean(LocalConfigLoader.getProperty("server.keep_alive.enabled", "true")))
                .timeoutSeconds(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.timeout_seconds", "60")))
                .maxRequests(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.max_requests", "100")))
                .timeoutCheckIntervalSeconds(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.timeout_check_interval_seconds", "30")))
                .build();
        
        // Initialize connection manager
        this.connectionManager = new ConnectionManager(keepAliveConfig);

        // Initialize router
        this.router = new Router(webRoot);

        // Initialize route configuration manager
        this.routeConfigManager = new RouteConfigManager(router);

        // Load initial route configuration
        this.configSource = new FallbackConfigSource(new NacosConfigSource(), new LocalConfigSource());
        this.configSource.onChange(this::reloadRouter);

        // Initialize routes if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.router.enabled", "true"))) {
            initializeRouter();
        }
        
        StaticFileCacheFactory.init();
        
        log.info("HTTP Keep-Alive enabled: {}, timeout: {}s, max requests: {}", 
                keepAliveConfig.isEnabled(), 
                keepAliveConfig.getTimeoutSeconds(), 
                keepAliveConfig.getMaxRequests());
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            log.info("Ocean is running at http://{}:{}", InetAddress.getLocalHost().getHostAddress(), port);
            log.info("Web root: {}", webRoot);
            while (isRunning) {
                Socket client = serverSocket.accept();
                threadPool.execute(new ClientHandler(client, webRoot, connectionManager, router));
            }
        } catch (Exception e) {
            log.error("Error starting Ocean: {}", e.getMessage(), e);
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                log.error("Error closing socket: {}", e.getMessage(), e);
            }
        }
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
        if (connectionManager != null) {
            try {
                connectionManager.close();
            } catch (Exception e) {
                log.error("Error closing connection manager: {}", e.getMessage(), e);
            }
        }
        log.info("Ocean has stopped.");
    }
    
    /**
     * 初始化路由器
     */
    private void initializeRouter() {
        try {
            List<RouteConfig> routeConfigs = configSource.load();
            router.registerRoutes(routeConfigs);
            routeConfigManager.initializeRoutes(routeConfigs);
            
            // 打印路由统计信息
            var stats = router.getRouteStats();
            log.info("Router initialized - Total routes: {}, Handler cache size: {}", 
                    stats.get("totalRoutes"), stats.get("handlerCacheSize"));
            
        } catch (Exception e) {
            log.error("Failed to initialize router: {}", e.getMessage(), e);
        }
    }

    private void reloadRouter() {
        try {
            List<RouteConfig> newRouteConfigs = configSource.load();
            routeConfigManager.refreshRoutes(newRouteConfigs);

            // 打印路由统计信息
            Map<String, Object> routeStats = router.getRouteStats();
            log.info("Router reloaded - Total routes: {}, Handler cache size: {}",
                    routeStats.get("totalRoutes"), routeStats.get("handlerCacheSize"));
        } catch (Exception e) {
            log.error("Failed to reload router: {}", e.getMessage(), e);
        }
    }
}
