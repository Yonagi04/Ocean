package com.yonagi.ocean.core.ratelimiter.algorithm;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 09:53
 */
public class TokenBucket implements RateLimiter {

    private final long capacity;

    private final double ratePerSecond;

    private final ReentrantLock lock = new ReentrantLock();

    private double tokens;
    private long lastRefillTimestamp;

    public TokenBucket(long capacity, double ratePerSecond) {
        this.capacity = capacity;
        this.ratePerSecond = ratePerSecond;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    @Override
    public boolean tryAcquire() {
        lock.lock();
        try {
            refill();
            if (tokens >= 1) {
                tokens -= 1.0;
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long timeElapsed = now - lastRefillTimestamp;
        if (timeElapsed <= 0) {
            return;
        }
        double tokensToAdd = (timeElapsed / 1000.0) * ratePerSecond;
        tokens = Math.min(capacity, tokensToAdd + tokens);
        lastRefillTimestamp = now;
    }
}
