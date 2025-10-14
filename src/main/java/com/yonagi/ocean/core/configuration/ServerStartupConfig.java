package com.yonagi.ocean.core.configuration;

import com.yonagi.ocean.utils.LocalConfigLoader;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 20:02
 */
public final class ServerStartupConfig {

    private final Integer httpPort;
    private final Integer sslPort;
    private final boolean sslEnabled;
    private final String webRoot;
    private final ThreadPoolExecutor threadPool;
    private final ThreadPoolExecutor listenerPool;
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
        this.threadPool = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.listenerPool = new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadPoolExecutor.AbortPolicy()
        );
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

    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    public ThreadPoolExecutor getListenerPool() {
        return listenerPool;
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
