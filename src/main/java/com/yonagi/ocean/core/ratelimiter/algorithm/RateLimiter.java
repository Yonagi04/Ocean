package com.yonagi.ocean.core.ratelimiter.algorithm;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 09:37
 */
public interface RateLimiter {

    boolean tryAcquire();
}
