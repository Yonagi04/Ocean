package com.yonagi.ocean.admin.health.impl;

import com.yonagi.ocean.admin.health.HealthComponent;
import com.yonagi.ocean.admin.health.HealthIndicator;
import com.yonagi.ocean.admin.health.enums.HealthStatus;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 17:23
 */
public class ThreadPoolHealthIndicator implements HealthIndicator {

    private final ThreadPoolExecutor threadPool;
    private static final int MAX_QUEUE_RATIO = 80;

    public ThreadPoolHealthIndicator(ThreadPoolExecutor threadPool) {
        this.threadPool = threadPool;
    }

    @Override
    public HealthComponent check() {
        int activeThreads = threadPool.getActiveCount();
        int queueSize = threadPool.getQueue().size();
        int maxPoolSize = threadPool.getMaximumPoolSize();

        long remainingCapacity = threadPool.getQueue().remainingCapacity();
        long totalCapacity = queueSize + remainingCapacity;

        Map<String, Object> details = Map.of(
                "activeThreads", activeThreads,
                "coreSize", threadPool.getCorePoolSize(),
                "maxSize", maxPoolSize,
                "queueSize", queueSize,
                "queueRemaining", remainingCapacity
        );

        if (activeThreads == maxPoolSize && remainingCapacity == 0) {
            return new HealthComponent(HealthStatus.DOWN, details);
        } else if (remainingCapacity * 100 / totalCapacity < (100 - MAX_QUEUE_RATIO)) {
            return new HealthComponent(HealthStatus.OUT_OF_SERVICE, details);
        } else {
            return new HealthComponent(HealthStatus.UP, details);
        }
    }

    @Override
    public String getName() {
        return "threadPool";
    }
}
