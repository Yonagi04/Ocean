package com.yonagi.ocean.core.loadbalance.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.util.concurrent.AtomicDouble;
import org.checkerframework.checker.guieffect.qual.UI;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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

    // 运行时状态：节点状态变更回调
    private transient Consumer<Upstream> onStageChange;

    @JsonCreator
    public Upstream(@JsonProperty("url") String url,
                    @JsonProperty("weight") double weight) {
        this.url = url;
        this.weight = Math.max(1, weight);
        this.currentWeight = new AtomicDouble(0.0d);
        this.effectiveWeight = new AtomicDouble(weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, weight);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        Upstream that = (Upstream) obj;
        return Objects.equals(url, that.url) && weight == that.weight;
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
        if (this.isHealthy.get() != healthy) {
            isHealthy.set(healthy);
            lastCheckedTime = System.currentTimeMillis();
            if (onStageChange != null) {
                onStageChange.accept(this);
            }
        }
    }

    public long getLastCheckedTime() {
        return lastCheckedTime;
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
        if (effectiveWeight != this.effectiveWeight.get()) {
            this.effectiveWeight.set(effectiveWeight);
            if (onStageChange != null) {
                onStageChange.accept(this);
            }
        }
    }

    public void setOnStageChange(Consumer<Upstream> listener) {
        this.onStageChange = listener;
    }

    @Override
    public String toString() {
        return String.format("%s (Weight: %f, Healthy: %b)", url, weight, isHealthy.get());
    }
}
