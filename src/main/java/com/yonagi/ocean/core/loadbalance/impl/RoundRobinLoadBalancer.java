package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.LoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.loadbalance.config.enums.HealthCheckMode;
import com.yonagi.ocean.core.protocol.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:35
 */
public class RoundRobinLoadBalancer extends AbstractLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    private final AtomicInteger counter = new AtomicInteger(0);

    public RoundRobinLoadBalancer(LoadBalancerConfig config) {
        super(config);
    }

    @Override
    public Upstream choose(HttpRequest request) {
        List<Upstream> healthyUpstreams = getHealthyUpstreams();
        if (healthyUpstreams.isEmpty()) {
            return null;
        }

        int index = counter.getAndIncrement() % healthyUpstreams.size();
        if (counter.get() > 1000000) {
            counter.set(0);
        }
        return healthyUpstreams.get(index);
    }
}
