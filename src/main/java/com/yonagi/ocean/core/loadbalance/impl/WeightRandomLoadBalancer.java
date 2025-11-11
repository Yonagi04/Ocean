package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/11 17:33
 */
public class WeightRandomLoadBalancer extends AbstractLoadBalancer {

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    private volatile double[] prefixSumWeights;
    private volatile long latestUpstreamsVersion = -1;

    private List<Upstream> healthyUpstreams;

    public WeightRandomLoadBalancer(LoadBalancerConfig config) {
        super(config);
    }

    @Override
    public Upstream choose(HttpRequest request) {
        readLock.lock();
        long currentVersion = computeVersion();
        if (currentVersion != latestUpstreamsVersion) {
            readLock.unlock();
            rebuildCache();
            latestUpstreamsVersion = currentVersion;
            readLock.lock();
        }

        double[] prefix = prefixSumWeights;
        if (prefix == null || prefix.length == 0) {
            return null;
        }
        double totalWeight = prefix[prefix.length - 1];
        double rand = ThreadLocalRandom.current().nextDouble(totalWeight);
        int idx = Arrays.binarySearch(prefix, rand + 1);
        if (idx < 0) {
            idx = -idx - 1;
        }
        readLock.unlock();
        return healthyUpstreams.get(idx);
    }

    // todo: 新增权重变更时的重载逻辑
    public void rebuildCache() {
        writeLock.lock();
        healthyUpstreams = getHealthyUpstreams();
        double[] prefix = new double[healthyUpstreams.size()];
        double sum = 0.0d;
        for (int i = 0; i < healthyUpstreams.size(); i++) {
            sum += healthyUpstreams.get(i).getEffectiveWeight().get();
            prefix[i] = sum;
        }
        this.prefixSumWeights = prefix;
        writeLock.unlock();
    }

    private long computeVersion() {
        long version = 0;
        for (Upstream upstream : config.getUpstreams()) {
            version += upstream.getVersion().get();
        }
        return version;
    }
}
