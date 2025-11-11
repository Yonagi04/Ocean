package com.yonagi.ocean.core.loadbalance;

import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.protocol.HttpRequest;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 18:59
 */
public interface LoadBalancer {

    Upstream choose(HttpRequest request);

    void reportFailure(String url, long failureTime);
}
