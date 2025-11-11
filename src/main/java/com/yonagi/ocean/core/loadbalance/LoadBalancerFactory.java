package com.yonagi.ocean.core.loadbalance;

import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.enums.Strategy;
import com.yonagi.ocean.core.loadbalance.impl.IpHashLoadBalancer;
import com.yonagi.ocean.core.loadbalance.impl.NoneLoadBalancer;
import com.yonagi.ocean.core.loadbalance.impl.RandomLoadBalancer;
import com.yonagi.ocean.core.loadbalance.impl.RoundRobinLoadBalancer;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:58
 */
public class LoadBalancerFactory {

    public static LoadBalancer createLoadBalancer(LoadBalancerConfig config) {
        Strategy strategy = config.getStrategy();
        return switch (strategy) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer(config);
            case IP_HASH -> new IpHashLoadBalancer(config);
            case RANDOM -> new RandomLoadBalancer(config);
            case NONE -> new NoneLoadBalancer(config);
        };
    }

    public static LoadBalancer createLoadBalancer(LoadBalancerConfig config, Strategy strategy) {
        return switch (strategy) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer(config);
            case IP_HASH -> new IpHashLoadBalancer(config);
            case RANDOM -> new RandomLoadBalancer(config);
            case NONE -> new NoneLoadBalancer(config);
        };
    }
}
