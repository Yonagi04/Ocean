package com.yonagi.ocean.core;

import com.yonagi.ocean.admin.health.HealthCheckService;
import com.yonagi.ocean.admin.health.HealthIndicator;
import com.yonagi.ocean.admin.health.impl.NacosHealthIndicator;
import com.yonagi.ocean.admin.health.impl.ThreadPoolHealthIndicator;
import com.yonagi.ocean.admin.health.impl.VirtualThreadHealthIndicator;
import com.yonagi.ocean.backup.BackupScheduler;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.config.KeepAliveConfig;
import com.yonagi.ocean.core.context.ConnectionContext;
import com.yonagi.ocean.core.context.EnvironmentInfo;
import com.yonagi.ocean.core.context.ServerContext;
import com.yonagi.ocean.core.cors.CorsManager;
import com.yonagi.ocean.core.ratelimiter.config.source.ConfigManager;
import com.yonagi.ocean.core.reverseproxy.ReverseProxyChecker;
import com.yonagi.ocean.core.reverseproxy.ReverseProxyManager;
import com.yonagi.ocean.core.gzip.GzipEncoderManager;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.ratelimiter.RateLimiterManager;
import com.yonagi.ocean.core.router.RouteManager;
import com.yonagi.ocean.core.config.ServerStartupConfig;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.admin.metrics.MetricsRegistry;
import com.yonagi.ocean.middleware.MiddlewareChain;
import com.yonagi.ocean.middleware.MiddlewareLoader;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private Integer httpPort;
    private String webRoot;

    // Virtual thread & thread pool
    private ExecutorService workerThreadExecutor;
    private ExecutorService listenerThreadExecutor;
    private Boolean virtualThreadsEnabled;

    private KeepAliveConfig keepAliveConfig;
    private ConnectionManager connectionManager;

    private Router router;
    private RouteManager routeManager;
    private com.yonagi.ocean.core.router.config.source.ConfigManager routeConfigManager;

    private RateLimiterChecker rateLimiterChecker;
    private RateLimiterManager rateLimiterManager;
    private ConfigManager ratelimitConfigManager;

    private ReverseProxyChecker reverseProxyChecker;
    private ReverseProxyManager reverseProxyManager;
    private com.yonagi.ocean.core.reverseproxy.config.source.ConfigManager reverseProxyConfigManager;

    private ServerContext serverContext;

    private int sslPort;
    private boolean sslEnabled;
    private boolean redirectSslEnabled;
    private SSLServerSocketFactory sslServerSocketFactory;

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    public HttpServer() {
        log.info("""
                
                 _______  _______  _______  _______  _      \s
                (  ___  )(  ____ \\(  ____ \\(  ___  )( (    /|
                | (   ) || (    \\/| (    \\/| (   ) ||  \\  ( |
                | |   | || |      | (__    | (___) ||   \\ | |
                | |   | || |      |  __)   |  ___  || (\\ \\) |
                | |   | || |      | (      | (   ) || | \\   |
                | (___) || (____/\\| (____/\\| )   ( || )  \\  |
                (_______)(_______/(_______/|/     \\||/    )_)
                                                            \s""");
        ServerStartupConfig startupConfig = new ServerStartupConfig();
        this.httpPort = startupConfig.getHttpPort();
        this.sslEnabled = startupConfig.isSslEnabled();
        this.sslPort = startupConfig.getSslPort();
        this.redirectSslEnabled = startupConfig.isRedirectSslEnabled();

        this.webRoot = startupConfig.getWebRoot();

        this.workerThreadExecutor = startupConfig.getWorkerThreadExecutor();
        this.listenerThreadExecutor = startupConfig.getListenerThreadExecutor();
        this.virtualThreadsEnabled = startupConfig.getVirtualThreadsEnabled();

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
        this.serverContext = new ServerContext(
                new MiddlewareChain(MiddlewareLoader.loadMiddlewares()),
                this.rateLimiterChecker,
                this.reverseProxyChecker,
                this.router,
                this.connectionManager,
                new MetricsRegistry(workerThreadExecutor, virtualThreadsEnabled),
                new HealthCheckService(createHealthIndicators()),
                new EnvironmentInfo(LocalConfigLoader.getProperty("server.version"))
        );

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

        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, stopping Ocean server...");
            stop();
        }, "Ocean-Shutdown-Hook"));
    }

    public void start() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Server is already running");
            return;
        }
        try {
            httpServerSocket = new ServerSocket(httpPort);
            listenerThreadExecutor.execute(new ListenerThread(httpServerSocket, httpPort, false, sslEnabled));
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
                listenerThreadExecutor.execute(new ListenerThread(httpsServerSocket, sslPort, true, sslEnabled));
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

    public void awaitShutdown() {
        if (virtualThreadsEnabled) {
            log.info("Server is running in Virtual Thread mode. Main thread will block until shutdown...");
        }

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            log.warn("Server await interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public boolean awaitShutdown(long timeout, TimeUnit unit) {
        try {
            return shutdownLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            log.warn("Server await interrupted", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void stop() {
        if (!isRunning.compareAndSet(true, false)) {
            log.warn("Server is not running or already stopped");
            return;
        }
        listenerThreadExecutor.shutdownNow();
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
        if (workerThreadExecutor != null && !workerThreadExecutor.isShutdown()) {
            workerThreadExecutor.shutdown();
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
        if (reverseProxyManager != null) {
            reverseProxyManager.shutdownAll();
        }
        BackupScheduler.shutdownAll();

        log.info("Ocean stopped.");
        shutdownLatch.countDown();
    }

    private List<HealthIndicator> createHealthIndicators() {
        List<HealthIndicator> indicators = new java.util.ArrayList<>();
        indicators.add(new NacosHealthIndicator());

        if (this.virtualThreadsEnabled) {
            indicators.add(new VirtualThreadHealthIndicator(workerThreadExecutor));
        } else {
            indicators.add(new ThreadPoolHealthIndicator((ThreadPoolExecutor) workerThreadExecutor));
        }
        return indicators;
    }

    private void initializeComponents(ServerStartupConfig startupConfig) {
        // Initialize connection manager
        this.connectionManager = new ConnectionManager(startupConfig.getKeepAliveConfig());

        // Initialize router
        this.router = new Router(webRoot);

        // Initialize router config manager and initial router config
        this.routeManager = new RouteManager(router);
        RouteManager.setInstance(this.routeManager);
        this.routeConfigManager = new com.yonagi.ocean.core.router.config.source.ConfigManager(NacosConfigLoader.getConfigService());
        // Initialize routes if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.router.enabled", "true"))) {
            this.routeManager.refreshRoutes(routeConfigManager);
        }
        // Register route config change listener
        this.routeConfigManager.onChange(() -> routeManager.refreshRoutes(routeConfigManager));

        // Initialize rate limiter manager and initial rate limit config
        this.rateLimiterManager = new RateLimiterManager();
        RateLimiterManager.setInstance(this.rateLimiterManager);
        this.ratelimitConfigManager = new ConfigManager(NacosConfigLoader.getConfigService());
        // Initialize rate limit if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.rate_limit.enabled", "true"))) {
            this.rateLimiterManager.refreshRateLimiter(ratelimitConfigManager);
            this.rateLimiterManager.preloadGlobalLimiter();
        }
        // Register rate limit config change listener
        this.ratelimitConfigManager.onChange(() -> rateLimiterManager.refreshRateLimiter(ratelimitConfigManager));

        this.rateLimiterChecker = new RateLimiterChecker(rateLimiterManager);

        // Initialize reverse proxy manager and initial reverse proxy config
        this.reverseProxyManager = new ReverseProxyManager();
        ReverseProxyManager.setInstance(this.reverseProxyManager);
        this.reverseProxyConfigManager = new com.yonagi.ocean.core.reverseproxy.config.source.ConfigManager(NacosConfigLoader.getConfigService());
        // Initialize reverse proxy if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.reverse_proxy.enabled", "true"))) {
            this.reverseProxyManager.refreshReverseProxyConfigs(reverseProxyConfigManager);
        }
        // Register reverse proxy config change listener
        this.reverseProxyConfigManager.onChange(() -> reverseProxyManager.refreshReverseProxyConfigs(reverseProxyConfigManager));

        this.reverseProxyChecker = new ReverseProxyChecker(reverseProxyManager);
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

            while (isRunning.get()) {
                try {
                    Socket client = serverSocket.accept();
                    ConnectionContext connectContext = new ConnectionContext(isSsl, sslEnabled, redirectSslEnabled, sslPort, serverContext);
                    workerThreadExecutor.execute(new ClientHandler(client, connectContext));
                } catch (Exception e) {
                    if (isRunning.get()) {
                        log.error("{} listener error on port {}: {}", isSsl ? "HTTPS" : "HTTP", port, e.getMessage());
                    } else {
                        log.info("{} listener on port {} stopped successfully.", isSsl ? "HTTPS" : "HTTP", port);
                    }
                }
            }
        }
    }
}
