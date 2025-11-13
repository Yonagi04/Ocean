package com.yonagi.ocean.core.loadbalance.impl;

import com.yonagi.ocean.core.loadbalance.AbstractLoadBalancer;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/11 09:46
 */
public class IpHashLoadBalancer extends AbstractLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(IpHashLoadBalancer.class);

    public IpHashLoadBalancer(LoadBalancerConfig config) {
        super(config);
    }

    @Override
    public Upstream choose(HttpRequest request) {
        List<Upstream> upstreams = selectHealthyUpstreams(getTargetUpstreams(request));
        if (upstreams.isEmpty()) {
            return null;
        }
        String clientIp = request.getAttribute().getClientIp();
        int listSize = upstreams.size();
        int hash = clientIp.hashCode();
        int startIndex = Math.abs(hash) % listSize;
        log.debug("Client IP: {}, Hash: {}, Start Index: {}", clientIp, hash, startIndex);
        for (int i = 0; i < listSize; i++) {
            int index = (startIndex + i) % listSize;
            Upstream upstream = upstreams.get(index);
            if (upstream.isHealthy()) {
                log.debug("Selected upstream: {} for client IP: {}", upstream, clientIp);
                return upstream;
            }
            log.debug("Upstream: {} is unhealthy, trying next", upstream);
        }
        log.error("All upstreams are unhealthy");
        return null;
    }
}
