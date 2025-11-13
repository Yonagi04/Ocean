package com.yonagi.ocean.core.loadbalance;

import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.loadbalance.config.enums.HealthCheckMode;
import com.yonagi.ocean.core.loadbalance.config.enums.Strategy;
import com.yonagi.ocean.core.loadbalance.impl.WeightRandomLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/11 10:11
 */
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final LoadBalancerConfig config;

    private final ScheduledExecutorService scheduler;

    public HealthChecker(LoadBalancerConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        if (config.getHealthCheckMode() == HealthCheckMode.ACTIVE_CHECK) {
            log.info("Starting ACTIVE health checks with interval {} ms", config.getCheckIntervalMs());
            scheduler.scheduleAtFixedRate(this::performCheck,
                    0,
                    config.getCheckIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void performCheck() {
        for (Upstream upstream : config.getUpstreams()) {
            boolean isHealthy = HttpClient.checkHealth(upstream);
            upstream.setHealthy(isHealthy);
        }
        for (Upstream upstream : config.getCanaryUpstreams()) {
            boolean isHealthy = HttpClient.checkHealth(upstream);
            upstream.setHealthy(isHealthy);
        }
    }

    public void stop() {
        scheduler.shutdownNow();
        log.info("Health checks stopped.");
    }
}
