package com.yonagi.ocean.core.ratelimiter;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 14:14
 */
public class RateLimitParams {

    private final int capacity;

    private final double rate;

    public RateLimitParams(int capacity, double rate) {
        this.capacity = capacity;
        this.rate = rate;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getRate() {
        return rate;
    }
}
