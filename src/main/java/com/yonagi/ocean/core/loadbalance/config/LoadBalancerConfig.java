package com.yonagi.ocean.core.loadbalance.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yonagi.ocean.core.loadbalance.config.enums.HealthCheckMode;
import com.yonagi.ocean.core.loadbalance.config.enums.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:00
 */
public final class LoadBalancerConfig {

    private final Strategy strategy;

    private final HealthCheckMode healthCheckMode;

    private final long checkIntervalMs;

    private final List<Upstream> upstreams;

    // 运行时状态：LB配置的版本号
    private final AtomicLong version = new AtomicLong(0);

    @Override
    public int hashCode() {
        return Objects.hash(strategy, healthCheckMode, checkIntervalMs, upstreams);
    }

    @JsonCreator
    public LoadBalancerConfig(@JsonProperty("strategy") Strategy strategy,
                              @JsonProperty("healthCheckMode") HealthCheckMode healthCheckMode,
                              @JsonProperty("checkIntervalMs") long checkIntervalMs,
                              @JsonProperty("upstreams") List<Upstream> upstreams) {
        this.strategy = strategy;
        this.healthCheckMode = healthCheckMode;
        this.checkIntervalMs = checkIntervalMs;
        this.upstreams = new ArrayList<>(upstreams);
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public HealthCheckMode getHealthCheckMode() {
        return healthCheckMode;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public List<Upstream> getUpstreams() {
        return upstreams;
    }

    public String getCacheKey() {
        return String.valueOf(this.hashCode());
    }

    public long getVersion() {
        return version.get();
    }

    public void setVersion(long version) {
        this.version.set(version);
    }
}
