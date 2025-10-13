package com.yonagi.ocean.core;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.configuration.KeepAliveConfig;
import com.yonagi.ocean.core.configuration.source.router.*;
import com.yonagi.ocean.core.gzip.GzipEncoderManager;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.ratelimiter.RateLimiterManager;
import com.yonagi.ocean.core.router.RouteManager;
import com.yonagi.ocean.core.configuration.ServerStartupConfig;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadPoolExecutor;

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

    private Router router;
    private RouteManager routeConfigManager;
    private ConfigSource routeConfigSource;

    private RateLimiterChecker rateLimiterChecker;
    private RateLimiterManager rateLimiterManager;
    private com.yonagi.ocean.core.configuration.source.ratelimit.ConfigSource rateLimitConfigSource;

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
        ServerStartupConfig startupConfig = new ServerStartupConfig();
        this.port = startupConfig.getPort();
        this.webRoot = startupConfig.getWebRoot();
        this.threadPool = startupConfig.getThreadPool();
        this.keepAliveConfig = startupConfig.getKeepAliveConfig();

        // Nacos Setup
        NacosConfigLoader.init();

        // Initialize static file cache
        StaticFileCacheFactory.init();

        // Initialize GZIP Encoder manager
        GzipEncoderManager.init();

        // Initialize core components
        initializeComponents(startupConfig);

        // Register route configuration change listener
        this.routeConfigSource.onChange(() -> routeConfigManager.refreshRoutes(routeConfigSource));

        // Register rate limit configuration change listener
        this.rateLimitConfigSource.onChange(() -> rateLimiterManager.refreshRateLimiter(rateLimitConfigSource));

        // Initialize routes if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.router.enabled", "true"))) {
            // this.routeConfigManager.initializeRoutes(routeConfigSource);
            this.routeConfigManager.refreshRoutes(routeConfigSource);
        }

        // Initialize rate limit if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.rate_limit.enabled", "true"))) {
            this.rateLimiterManager.refreshRateLimiter(rateLimitConfigSource);
            this.rateLimiterManager.preloadGlobalLimiter();
        }
        
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
                threadPool.execute(new ClientHandler(client, webRoot, connectionManager, router, rateLimiterChecker));
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
        if (router != null) {
            router.shutdown();
        }

        log.info("Ocean has stopped.");
    }

    private void initializeComponents(ServerStartupConfig startupConfig) {
        // Initialize connection manager
        this.connectionManager = new ConnectionManager(startupConfig.getKeepAliveConfig());

        // Initialize router, router configuration manager and initial router configuration
        this.router = new Router(webRoot);
        ConfigService initialConfigService = NacosConfigLoader.getConfigService();
        NacosConfigSource initialNacosRouteSource = new NacosConfigSource(initialConfigService);
        MutableConfigSource routeProxy = new MutableConfigSource(initialNacosRouteSource);
        this.routeConfigSource = new FallbackConfigSource(
                routeProxy,
                new LocalConfigSource()
        );

        this.routeConfigManager = new RouteManager(router);
        RouteManager.setInstance(this.routeConfigManager);

        RouteManager.RouteConfigRecoveryAction routeConfigRecoveryAction = new RouteManager.RouteConfigRecoveryAction(
                routeProxy, this.routeConfigManager
        );
        NacosConfigLoader.registerRecoveryAction(routeConfigRecoveryAction);

        // Initialize rate limiter manager and initial rate limit configuration
        com.yonagi.ocean.core.configuration.source.ratelimit.NacosConfigSource initialNacosRateLimiterSource =
                new com.yonagi.ocean.core.configuration.source.ratelimit.NacosConfigSource(initialConfigService);
        com.yonagi.ocean.core.configuration.source.ratelimit.MutableConfigSource ratelimitProxy =
                new com.yonagi.ocean.core.configuration.source.ratelimit.MutableConfigSource(initialNacosRateLimiterSource);

        this.rateLimitConfigSource = new com.yonagi.ocean.core.configuration.source.ratelimit.FallbackConfigSource(
                ratelimitProxy,
                new com.yonagi.ocean.core.configuration.source.ratelimit.LocalConfigSource()
        );
        this.rateLimiterManager = new RateLimiterManager();
        RateLimiterManager.setInstance(this.rateLimiterManager);

        RateLimiterManager.RateLimiterConfigRecoveryAction rateLimiterConfigRecoveryAction =
                new RateLimiterManager.RateLimiterConfigRecoveryAction(ratelimitProxy, this.rateLimiterManager);
        NacosConfigLoader.registerRecoveryAction(rateLimiterConfigRecoveryAction);

        this.rateLimiterChecker = new RateLimiterChecker(rateLimiterManager);
    }
}
