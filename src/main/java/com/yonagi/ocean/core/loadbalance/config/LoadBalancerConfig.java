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

    private final Long checkIntervalMs;

    private final List<Upstream> upstreams;

    private final List<Upstream> canaryUpstreams;

    private final Integer canaryPercent;

    // 运行时状态：LB配置的版本号
    private final AtomicLong version = new AtomicLong(0);

    @Override
    public int hashCode() {
        return Objects.hash(strategy, healthCheckMode, checkIntervalMs, upstreams, canaryUpstreams, canaryPercent);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        LoadBalancerConfig that = (LoadBalancerConfig) obj;
        return Objects.equals(strategy, that.strategy) &&
                Objects.equals(healthCheckMode, that.healthCheckMode) &&
                Objects.equals(checkIntervalMs, that.checkIntervalMs) &&
                Objects.equals(upstreams, that.upstreams) &&
                Objects.equals(canaryUpstreams, that.canaryUpstreams) &&
                Objects.equals(canaryPercent, that.canaryPercent);
    }

    @JsonCreator
    public LoadBalancerConfig(@JsonProperty("strategy") Strategy strategy,
                              @JsonProperty("healthCheckMode") HealthCheckMode healthCheckMode,
                              @JsonProperty("checkIntervalMs") Long checkIntervalMs,
                              @JsonProperty("upstreams") List<Upstream> upstreams,
                              @JsonProperty("canaryUpstreams") List<Upstream> canaryUpstreams,
                              @JsonProperty("canaryPercent") Integer canaryPercent) {
        this.strategy = strategy;
        this.healthCheckMode = healthCheckMode;
        this.checkIntervalMs = checkIntervalMs;
        this.upstreams = new ArrayList<>(upstreams);
        this.canaryUpstreams = new ArrayList<>(canaryUpstreams);
        this.canaryPercent = canaryPercent;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public HealthCheckMode getHealthCheckMode() {
        return healthCheckMode;
    }

    public Long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public List<Upstream> getUpstreams() {
        return upstreams;
    }

    public List<Upstream> getCanaryUpstreams() {
        return canaryUpstreams;
    }

    public Integer getCanaryPercent() {
        return canaryPercent;
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
