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
public class ServerStartupConfig {

    private final Integer port;
    private final String webRoot;
    private final ThreadPoolExecutor threadPool;
    private final KeepAliveConfig keepAliveConfig;

    public ServerStartupConfig() {
        int corePoolSize = Math.max(Runtime.getRuntime().availableProcessors(),
                LocalConfigLoader.getProperty("server.thread_pool.core_size") == null ? 2 : Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.core_size")));
        int maximumPoolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1,
                LocalConfigLoader.getProperty("server.thread_pool.max_size") == null ? 4 : Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.max_size")));
        long keepAliveTime = Math.max(60L,
                LocalConfigLoader.getProperty("server.thread_pool.keep_alive_seconds") == null ? 60L : Long.parseLong(LocalConfigLoader.getProperty("server.thread_pool.keep_alive_seconds")));
        int queueCapacity = Math.max(1000,
                LocalConfigLoader.getProperty("server.thread_pool.queue_capacity") == null ? 1000 : Integer.parseInt(LocalConfigLoader.getProperty("server.thread_pool.queue_capacity")));

        this.port = Integer.parseInt(LocalConfigLoader.getProperty("server.port", "8080"));
        this.webRoot = LocalConfigLoader.getProperty("server.webroot", "/www");
        this.threadPool = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.keepAliveConfig = new KeepAliveConfig.Builder()
                .enabled(Boolean.parseBoolean(LocalConfigLoader.getProperty("server.keep_alive.enabled", "true")))
                .timeoutSeconds(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.timeout_seconds", "60")))
                .maxRequests(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.max_requests", "100")))
                .timeoutCheckIntervalSeconds(Integer.parseInt(LocalConfigLoader.getProperty("server.keep_alive.timeout_check_interval_seconds", "30")))
                .build();
    }

    public Integer getPort() {
        return port;
    }

    public String getWebRoot() {
        return webRoot;
    }

    public ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    public KeepAliveConfig getKeepAliveConfig() {
        return keepAliveConfig;
    }
}
