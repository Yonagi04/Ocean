package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:56
 */
public class NoneLoadBalancer extends AbstractLoadBalancer {

    public NoneLoadBalancer(LoadBalancerConfig config) {
        super(config);
    }

    @Override
    public Upstream choose(HttpRequest request) {
        List<Upstream> healthyUpstreams = getHealthyUpstreams();
        if (healthyUpstreams.isEmpty()) {
            return null;
        }
        return healthyUpstreams.getFirst();
    }
}
