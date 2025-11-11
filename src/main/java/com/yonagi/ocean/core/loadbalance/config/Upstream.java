package com.yonagi.ocean.core.loadbalance.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 18:51
 */
public class Upstream {

    private final String url;

    private final double weight;

    // 运行时状态：节点的健康状态，默认为true
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);

    // 运行时状态：节点健康状态是否在验证
    private final AtomicBoolean recovering = new AtomicBoolean(false);

    // 运行时状态：上次健康检查时间
    private volatile long lastCheckedTime = System.currentTimeMillis();

    @JsonCreator
    public Upstream(@JsonProperty("url") String url,
                    @JsonProperty("weight") double weight) {
        this.url = url;
        this.weight = Math.max(1, weight);
    }

    public String getUrl() {
        return url;
    }

    public double getWeight() {
        return weight;
    }

    public boolean isHealthy() {
        return isHealthy.get();
    }

    public void setHealthy(boolean healthy) {
        isHealthy.set(healthy);
        lastCheckedTime = System.currentTimeMillis();
    }

    public long getLastCheckedTime() {
        return lastCheckedTime;
    }

    public AtomicBoolean getRecovering() {
        return recovering;
    }

    @Override
    public String toString() {
        return String.format("%s (Weight: %f, Healthy: %b)", url, weight, isHealthy.get());
    }
}
