package com.yonagi.ocean.core.loadbalance;

import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.enums.Strategy;
import com.yonagi.ocean.core.loadbalance.impl.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:58
 */
public class LoadBalancerFactory {

    private static final Map<String, LoadBalancer> LOAD_BALANCER_CACHE = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);
    private static final AtomicBoolean enableScheduler = new AtomicBoolean(false);

    public static LoadBalancer createOrGetLoadBalancer(LoadBalancerConfig config) {
        startCacheClearScheduler();
        Strategy strategy = config.getStrategy();
        String cacheKey = config.getCacheKey();
        return LOAD_BALANCER_CACHE.computeIfAbsent(cacheKey, k -> createLoadBalancer(config, strategy));
    }

    public static LoadBalancer createLoadBalancer(LoadBalancerConfig config, Strategy strategy) {
        return switch (strategy) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer(config);
            case IP_HASH -> new IpHashLoadBalancer(config);
            case RANDOM -> new RandomLoadBalancer(config);
            case NONE -> new NoneLoadBalancer(config);
            case WEIGHT_ROUND_ROBIN -> new WeightRoundRobinLoadBalancer(config);
            case WEIGHT_RANDOM -> new WeightRandomLoadBalancer(config);
        };
    }

    private static void startCacheClearScheduler() {
        if (enableScheduler.compareAndSet(false, true)) {
            SCHEDULER.scheduleAtFixedRate(LOAD_BALANCER_CACHE::clear, 1, 1, TimeUnit.DAYS);
        }
    }
}
