package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.LoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:50
 */
public class RandomLoadBalancer extends AbstractLoadBalancer {

    private final Random random = new Random();

    public RandomLoadBalancer(LoadBalancerConfig config) {
        super(config);
    }

    @Override
    public Upstream choose(HttpRequest request) {
        List<Upstream> healthyUpstreams = selectHealthyUpstreams(getTargetUpstreams(request));
        if (healthyUpstreams.isEmpty()) {
            return null;
        }

        int index = random.nextInt(healthyUpstreams.size());
        return healthyUpstreams.get(index);
    }
}
