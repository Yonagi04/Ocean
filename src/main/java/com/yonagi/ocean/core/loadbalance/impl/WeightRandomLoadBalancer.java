package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.reverseproxy.HttpClientManager;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/11 17:33
 */
public class WeightRandomLoadBalancer extends AbstractLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(WeightRandomLoadBalancer.class);

    private static final int CLIENT_REMOVE_DELAY_MS = Integer.parseInt(LocalConfigLoader.getProperty("server.reverse_proxy.client_remove_delay_millis", "3000"));

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WeightRandomLB-Rebuilder");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean rebuildPending = new AtomicBoolean(false);

    private volatile double[] prefixSumWeights;
    private volatile long latestLbConfigVersion = -1L;

    private List<Upstream> healthyUpstreams;

    public WeightRandomLoadBalancer(LoadBalancerConfig config) {
        super(config);
        for (Upstream upstream : config.getUpstreams()) {
            upstream.setOnStageChange(this::onUpstreamChanged);
        }
        rebuildCache();
    }

    @Override
    public Upstream choose(HttpRequest request) {
        readLock.lock();
        try {
            long currentLbConfigVersion = config.getVersion();
            if (currentLbConfigVersion != latestLbConfigVersion) {
                readLock.unlock();
                rebuildCache();
                readLock.lock();
            }

            double[] prefix = prefixSumWeights;
            if (prefix == null || prefix.length == 0) {
                return null;
            }
            double totalWeight = prefix[prefix.length - 1];
            double rand = ThreadLocalRandom.current().nextDouble(totalWeight);
            int idx = Arrays.binarySearch(prefix, rand);
            if (idx < 0) {
                idx = -idx - 1;
            }
            return healthyUpstreams.get(idx);
        } finally {
            readLock.unlock();
        }
    }

    public void rebuildCache() {
        writeLock.lock();
        try {
            this.latestLbConfigVersion = config.getVersion();
            healthyUpstreams = getHealthyUpstreams();
            double[] prefix = new double[healthyUpstreams.size()];
            double sum = 0.0d;
            for (int i = 0; i < healthyUpstreams.size(); i++) {
                sum += healthyUpstreams.get(i).getEffectiveWeight().get();
                prefix[i] = sum;
            }
            this.prefixSumWeights = prefix;
        } finally {
            writeLock.unlock();
        }
    }

    private void onUpstreamChanged(Upstream upstream) {
        if (!rebuildPending.compareAndSet(false, true)) {
            return;
        }
        scheduler.schedule(() -> {
            rebuildCache();
            if (!upstream.isHealthy()) {
                scheduler.schedule(() -> {
                    HttpClientManager.removeClient(upstream.getUrl());
                }, CLIENT_REMOVE_DELAY_MS, TimeUnit.MILLISECONDS);
            }
            rebuildPending.set(false);
            log.debug("Rebuilt prefix cache due to upstream change: {}", upstream.getUrl());
        }, 200, TimeUnit.MILLISECONDS);
    }
}
