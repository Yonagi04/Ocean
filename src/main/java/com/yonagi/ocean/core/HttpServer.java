package com.yonagi.ocean.core;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.configuration.KeepAliveConfig;
import com.yonagi.ocean.core.configuration.source.router.*;
import com.yonagi.ocean.core.context.ConnectionContext;
import com.yonagi.ocean.core.context.ServerContext;
import com.yonagi.ocean.framework.ControllerRegistry;
import com.yonagi.ocean.core.gzip.GzipEncoderManager;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.ratelimiter.RateLimiterManager;
import com.yonagi.ocean.core.router.RouteManager;
import com.yonagi.ocean.core.configuration.ServerStartupConfig;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.middleware.MiddlewareChain;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/03 17:33
 */
public class HttpServer {

    private ServerSocket httpServerSocket;
    private ServerSocket httpsServerSocket;
    private Boolean isRunning;

    private Integer httpPort;
    private String webRoot;

    private ThreadPoolExecutor threadPool;
    private ThreadPoolExecutor listenerPool;
    private KeepAliveConfig keepAliveConfig;
    private ConnectionManager connectionManager;

    private Router router;
    private RouteManager routeConfigManager;
    private ConfigSource routeConfigSource;

    private RateLimiterChecker rateLimiterChecker;
    private RateLimiterManager rateLimiterManager;
    private com.yonagi.ocean.core.configuration.source.ratelimit.ConfigSource rateLimitConfigSource;
    private ServerContext serverContext;

    private int sslPort;
    private boolean sslEnabled;
    private boolean redirectSslEnabled;
    private SSLServerSocketFactory sslServerSocketFactory;

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
        this.httpPort = startupConfig.getHttpPort();
        this.sslEnabled = startupConfig.isSslEnabled();
        this.sslPort = startupConfig.getSslPort();
        this.redirectSslEnabled = startupConfig.isRedirectSslEnabled();

        this.webRoot = startupConfig.getWebRoot();
        this.threadPool = startupConfig.getThreadPool();
        this.listenerPool = startupConfig.getListenerPool();
        this.keepAliveConfig = startupConfig.getKeepAliveConfig();

        // Nacos Setup
        NacosConfigLoader.init();

        // Initialize static file cache
        StaticFileCacheFactory.init();

        // Initialize GZIP Encoder manager
        GzipEncoderManager.init();

        // Initialize CORS manager
        CorsManager.init();

        // Initialize core components
        initializeComponents(startupConfig);
        this.serverContext = new ServerContext(new MiddlewareChain(), this.rateLimiterChecker, this.router, this.connectionManager);

        log.info("HTTP Keep-Alive enabled: {}, timeout: {}s, max requests: {}",
                keepAliveConfig.isEnabled(),
                keepAliveConfig.getTimeoutSeconds(),
                keepAliveConfig.getMaxRequests());

        if (this.sslEnabled) {
            try {
                initSSL(startupConfig);
            } catch (Exception e) {
                log.error("Failed to initialize SSL context. HTTPS listener will not start: {}", e.getMessage(), e);
                this.sslEnabled = false;
            }
        }
    }

    public void start() {
        isRunning = true;
        try {
            httpServerSocket = new ServerSocket(httpPort);
            listenerPool.execute(new ListenerThread(httpServerSocket, httpPort, false, sslEnabled));
            log.info("Ocean HTTP listener is running at http://{}:{}", InetAddress.getLocalHost().getHostAddress(), httpPort);
        } catch (Exception e) {
            log.error("Failed to start HTTP listener on port {}: {}", httpPort, e.getMessage(), e);
        }
        if (sslEnabled && sslServerSocketFactory != null) {
            try {
                httpsServerSocket = sslServerSocketFactory.createServerSocket(sslPort);
                if (httpsServerSocket instanceof SSLServerSocket) {
                    SSLServerSocket sslSock = (SSLServerSocket) httpsServerSocket;
                    sslSock.setNeedClientAuth(false);
                    sslSock.setWantClientAuth(false);
                    sslSock.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                    sslSock.setEnabledCipherSuites(new String[]{
                            "TLS_AES_256_GCM_SHA384",
                            "TLS_AES_128_GCM_SHA256",
                            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
                    });
                    log.info("HTTPS server configured for single-way (server) authentication only");
                } else {
                    log.warn("HTTPS ServerSocket is not an instance of SSLServerSocket. Cannot disable client auth");
                }
                listenerPool.execute(new ListenerThread(httpsServerSocket, sslPort, true, sslEnabled));
                log.info("Ocean HTTPS listener is running at https://{}:{}", InetAddress.getLocalHost().getHostAddress(), sslPort);
            } catch (Exception e) {
                log.error("Failed to start HTTPS listener on port {}: {}", sslPort, e.getMessage(), e);
            }
        }
        log.info("Web root: {}", webRoot);
        if (!httpServerSocket.isBound() && (httpsServerSocket == null || !httpsServerSocket.isBound())) {
            log.error("Server failed to start any listeners");
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        listenerPool.shutdownNow();
        try {
            if (httpServerSocket != null && !httpServerSocket.isClosed()) {
                httpServerSocket.close();
            }
        } catch (Exception e) {
            log.warn("Error closing HTTP server socket: {}", e.getMessage());
        }
        try {
            if (httpsServerSocket != null && !httpsServerSocket.isClosed()) {
                httpsServerSocket.close();
            }
        } catch (Exception e) {
            log.warn("Error closing HTTPS server socket: {}", e.getMessage());
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

        log.info("Ocean stopped.");
    }

    private void initializeComponents(ServerStartupConfig startupConfig) {
        // Initialize connection manager
        this.connectionManager = new ConnectionManager(startupConfig.getKeepAliveConfig());

        // Initialize router
        this.router = new Router(webRoot);

        // Scan Controller and register to Static Router
        final String DEFAULT_BASE_PACKAGE = "";
        String basePackageToScan = LocalConfigLoader.getProperty("server.controller.base_package", DEFAULT_BASE_PACKAGE);
        if (basePackageToScan.equals(DEFAULT_BASE_PACKAGE)) {
            log.info("Starting Controller scanning using IMPLICIT global classpath search. To optimize startup time, set 'server.controller.base_package'.");
        } else {
            log.info("Starting Controller scanning in EXPLICIT base package: {}", basePackageToScan);
        }

        ControllerRegistry controllerRegistry = new ControllerRegistry(this.router, basePackageToScan);
        controllerRegistry.scanAndRegister();

        log.info("Controller scanning and static route registration completed.");

        // Initialize router configuration manager and initial router configuration
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

        // Register route configuration change listener
        this.routeConfigSource.onChange(() -> routeConfigManager.refreshRoutes(routeConfigSource));

        // Register rate limit configuration change listener
        this.rateLimitConfigSource.onChange(() -> rateLimiterManager.refreshRateLimiter(rateLimitConfigSource));

        // Initialize routes if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.router.enabled", "true"))) {
            this.routeConfigManager.refreshRoutes(routeConfigSource);
        }

        // Initialize rate limit if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.rate_limit.enabled", "true"))) {
            this.rateLimiterManager.refreshRateLimiter(rateLimitConfigSource);
            this.rateLimiterManager.preloadGlobalLimiter();
        }
    }

    private void initSSL(ServerStartupConfig startupConfig) throws Exception {
        log.info("Initializing SSL context for HTTPS");

        KeyStore keyStore = KeyStore.getInstance(startupConfig.getKeyStoreType());
        try (FileInputStream fis = new FileInputStream(startupConfig.getKeyStorePath())) {
            keyStore.load(fis, startupConfig.getKeyStorePassword().toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, startupConfig.getKeyPassword().toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), null, null);

        this.sslServerSocketFactory = sslContext.getServerSocketFactory();
        log.info("SSL Context initialization complete.");
    }

    private class ListenerThread implements Runnable {
        private final ServerSocket serverSocket;
        private final int port;
        private final boolean isSsl;
        private final boolean sslEnabled;

        public ListenerThread(ServerSocket serverSocket, int port, boolean isSsl, boolean sslEnabled) {
            this.serverSocket = serverSocket;
            this.port = port;
            this.isSsl = isSsl;
            this.sslEnabled = sslEnabled;
        }

        @Override
        public void run() {
            log.info("{} listener started on port {}", isSsl ? "HTTPS" : "HTTP", port);

            while (isRunning) {
                try {
                    Socket client = serverSocket.accept();
                    ConnectionContext connectContext = new ConnectionContext(isSsl, sslEnabled, redirectSslEnabled, sslPort, serverContext);
                    threadPool.execute(new ClientHandler(client, connectContext));
                } catch (Exception e) {
                    if (isRunning) {
                        log.error("{} listener error on port {}: {}", isSsl ? "HTTPS" : "HTTP", port, e.getMessage());
                    } else {
                        log.info("{} listener on port {} stopped successfully.", isSsl ? "HTTPS" : "HTTP", port);
                    }
                }
            }
        }
    }
}
