package com.yonagi.ocean.core.router;

import com.yonagi.ocean.handler.RequestHandler;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 17:42
 */
public class CacheEntry {
    private final RequestHandler handler;
    private final long expireTime;
    private long lastAccessTime;

    public CacheEntry(RequestHandler handler, long ttlMs) {
        this.handler = handler;
        this.expireTime = System.currentTimeMillis() + ttlMs;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public RequestHandler getHandler() {
        this.lastAccessTime = System.currentTimeMillis();
        return handler;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }
}
