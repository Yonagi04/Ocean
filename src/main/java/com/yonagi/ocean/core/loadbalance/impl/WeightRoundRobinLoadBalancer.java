package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/11 17:41
 */
public class WeightRoundRobinLoadBalancer extends AbstractLoadBalancer {

    public WeightRoundRobinLoadBalancer(LoadBalancerConfig config) {
        super(config);
    }

    @Override
    public Upstream choose(HttpRequest request) {
        double total = 0.0d;
        Upstream selected = null;
        for (Upstream upstream : selectHealthyUpstreams(getTargetUpstreams(request))) {
            upstream.getCurrentWeight().addAndGet(upstream.getEffectiveWeight().get());
            total += upstream.getEffectiveWeight().get();
            if (selected == null || upstream.getCurrentWeight().get() > selected.getCurrentWeight().get()) {
                selected = upstream;
            }
        }
        if (selected != null) {
            selected.getCurrentWeight().addAndGet(-total);
        }
        return selected;
    }
}
