package com.yonagi.ocean.core.config;

import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 20:02
 */
public final class ServerStartupConfig {

    private static final Logger log = LoggerFactory.getLogger(ServerStartupConfig.class);

    private final Integer httpPort;
    private final Integer sslPort;
    private final boolean sslEnabled;
    private final String webRoot;

    // Virtual thread & fallback thread pool
    private ExecutorService workerThreadExecutor;
    private ExecutorService listenerThreadExecutor;
    private final Boolean virtualThreadsEnabled;

    private final KeepAliveConfig keepAliveConfig;

    private final String keyStorePath;
    private final String keyStoreType;
    private final String keyStorePassword;
    private final String keyAlias;
    private final String keyPassword;
    private final boolean redirectSslEnabled;

    public ServerStartupConfig() {
        int corePoolSize = Math.max(Runtime.getRuntime().availableProcessors(),
                Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.core_size", "2")));
        int maximumPoolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1,
                Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.max_size", "4")));
        long keepAliveTime = Math.max(60L, Long.parseLong(LocalConfigLoader.getProperty("server.thread_pool.keep_alive_seconds", "60L")));
        int queueCapacity = Math.max(1000,
                Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.queue_capacity", "1000")));

        this.httpPort = Integer.parseInt(LocalConfigLoader.getProperty("server.port", "8080"));
        this.sslPort = Integer.parseInt(LocalConfigLoader.getProperty("server.ssl.port", "8443"));

        this.webRoot = LocalConfigLoader.getProperty("server.webroot", "/www");

        this.virtualThreadsEnabled = detectVirtualThreadSupport();
        if (virtualThreadsEnabled) {
            log.info("Using virtual thread support");
            initializeVirtualThreadExecutors();
        } else {
            log.warn("Virtual thread support disabled, please check properties, or ensure your JVM supports virtual threads and enable preview features if necessary.");
            initializeTraditionalThreadPools(corePoolSize, maximumPoolSize, keepAliveTime, queueCapacity);
        }

        this.keepAliveConfig = new KeepAliveConfig.Builder()
                .enabled(Boolean.parseBoolean(LocalConfigLoader.getProperty("server.keep_alive.enabled", "true")))
                .timeoutSeconds(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.timeout_seconds", "60")))
                .maxRequests(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.max_requests", "100")))
                .timeoutCheckIntervalSeconds(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.timeout_check_interval_seconds", "30")))
                .build();

        this.sslEnabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.enabled", "false"));
        this.keyStoreType = LocalConfigLoader.getProperty("server.ssl.key_store_type", "JKS");
        this.keyStorePath = LocalConfigLoader.getProperty("server.ssl.key_store_path");
        this.keyStorePassword = LocalConfigLoader.getProperty("server.ssl.key_store_password");
        this.keyAlias = LocalConfigLoader.getProperty("server.ssl.key_alias");
        this.keyPassword = LocalConfigLoader.getProperty("server.ssl.key_password");
        this.redirectSslEnabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.redirect_ssl", "false"));
    }

    private boolean detectVirtualThreadSupport() {
        if (!Boolean.parseBoolean(LocalConfigLoader.getProperty("server.thread.virtual_threads_enabled", "false"))) {
            return false;
        }
        String javaVersion = System.getProperty("java.version");
        int majorVersion = extractMajorVersion(javaVersion);
        if (majorVersion >= 21) {
            return testVirtualThreadCreation();
        } else if (majorVersion >= 19) {
            if (isPreviewEnabled()) {
                return testVirtualThreadCreation();
            } else {
                return false;
            }
        }
        return false;
    }

    private int extractMajorVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts[0].equals("1")) {
                return Integer.parseInt(parts[1]);
            } else {
                return Integer.parseInt(parts[0]);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isPreviewEnabled() {
        try {
            List<String> inputArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            boolean previewEnabled = inputArguments.stream()
                    .anyMatch(arg -> arg.equals("--enable-preview") || arg.equals("-XX:+EnablePreview"));
            return previewEnabled;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testVirtualThreadCreation() {
        try {
            Class<?> threadClass = Thread.class;
            var builderMethod = threadClass.getMethod("ofVirtual");
            var builder = builderMethod.invoke(null);

            var executorMethod = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            ExecutorService testExecutor = (ExecutorService) executorMethod.invoke(null);

            testExecutor.submit(() -> {
                log.debug("Virtual Thread test task executed on: {}", Thread.currentThread());
            }).get(1, TimeUnit.SECONDS);

            testExecutor.shutdown();
            testExecutor.awaitTermination(5, TimeUnit.SECONDS);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeVirtualThreadExecutors() {
        try {
            Method newVirtualThreadPerTaskExecutor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            this.workerThreadExecutor = (ExecutorService) newVirtualThreadPerTaskExecutor.invoke(null);
            this.listenerThreadExecutor = (ExecutorService) newVirtualThreadPerTaskExecutor.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Virtual Thread initialization failed", e);
        }
    }

    private void initializeTraditionalThreadPools(int corePoolSize, int maximumPoolSize, long keepAliveTime, int queueCapacity) {
        this.workerThreadExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.listenerThreadExecutor = new ThreadPoolExecutor(
                1,
                2,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public int getHttpPort() {
        return httpPort;
    }

    public int getSslPort() {
        return sslPort;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public String getWebRoot() {
        return webRoot;
    }

    public ExecutorService getWorkerThreadExecutor() {
        return workerThreadExecutor;
    }

    public ExecutorService getListenerThreadExecutor() {
        return listenerThreadExecutor;
    }

    public Boolean getVirtualThreadsEnabled() {
        return virtualThreadsEnabled;
    }

    public KeepAliveConfig getKeepAliveConfig() {
        return keepAliveConfig;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public boolean isRedirectSslEnabled() {
        return redirectSslEnabled;
    }
}
