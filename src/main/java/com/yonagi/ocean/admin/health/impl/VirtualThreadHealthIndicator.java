package com.yonagi.ocean.admin.health.impl;

import com.yonagi.ocean.admin.health.HealthComponent;
import com.yonagi.ocean.admin.health.HealthIndicator;
import com.yonagi.ocean.admin.health.enums.HealthStatus;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/05 13:34
 */
public class VirtualThreadHealthIndicator implements HealthIndicator {

    private final ExecutorService executor;

    public VirtualThreadHealthIndicator(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public HealthComponent check() {
        Map<String, Object> details = Map.of(
                "executorType", "VirtualThreadPerTaskExecutor",
                "concurrencyModel", "Unbounded (Low Overhead)",
                "note", "Actual load should be monitored via application metrics (e.g., active requests counter), not executor state."
        );

        if (executor.isShutdown()) {
            return new HealthComponent(HealthStatus.DOWN, details);
        } else {
            return new HealthComponent(HealthStatus.UP, details);
        }
    }

    @Override
    public String getName() {
        return "virtualThread";
    }
}
