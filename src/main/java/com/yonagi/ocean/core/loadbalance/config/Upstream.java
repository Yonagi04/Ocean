package com.yonagi.ocean.core.loadbalance.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.util.concurrent.AtomicDouble;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    // 运行时状态：重试次数
    private final AtomicInteger retryCount = new AtomicInteger(0);

    // 运行时状态：当前权重，用于加权轮询
    private final AtomicDouble currentWeight;

    // 运行时状态： 节点的有效权重，用于加权轮询
    private final AtomicDouble effectiveWeight;

    private final AtomicLong version = new AtomicLong(0);

    @JsonCreator
    public Upstream(@JsonProperty("url") String url,
                    @JsonProperty("weight") double weight) {
        this.url = url;
        this.weight = Math.max(1, weight);
        this.currentWeight = new AtomicDouble(0.0d);
        this.effectiveWeight = new AtomicDouble(weight);
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
        version.incrementAndGet();
        lastCheckedTime = System.currentTimeMillis();
    }

    public long getLastCheckedTime() {
        return lastCheckedTime;
    }

    public AtomicLong getVersion() {
        return version;
    }

    public AtomicInteger getRetryCount() {
        return retryCount;
    }

    public AtomicBoolean getRecovering() {
        return recovering;
    }

    public AtomicDouble getCurrentWeight() {
        return currentWeight;
    }

    public AtomicDouble getEffectiveWeight() {
        return effectiveWeight;
    }

    public void setEffectiveWeight(double effectiveWeight) {
        this.effectiveWeight.set(effectiveWeight);
        version.incrementAndGet();
    }

    @Override
    public String toString() {
        return String.format("%s (Weight: %f, Healthy: %b)", url, weight, isHealthy.get());
    }
}
