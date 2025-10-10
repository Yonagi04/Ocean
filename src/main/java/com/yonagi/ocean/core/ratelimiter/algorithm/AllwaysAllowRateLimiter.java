package com.yonagi.ocean.core.ratelimiter.algorithm;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 14:56
 */
public class AllwaysAllowRateLimiter implements RateLimiter {

    @Override
    public boolean tryAcquire() {
        return true;
    }
}
