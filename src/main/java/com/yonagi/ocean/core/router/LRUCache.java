package com.yonagi.ocean.core.router;

import com.yonagi.ocean.handler.RequestHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 17:42
 */
public class LRUCache<K, V> {
    private final int maxSize;
    private final long ttlMs;
    private final Map<K, CacheEntry> cache;
    private final LinkedHashMap<K, Long> accessOrder;

    public LRUCache(int maxSize, long ttlMs) {
        this.maxSize = maxSize;
        this.ttlMs = ttlMs;
        this.cache = new ConcurrentHashMap<>();
        this.accessOrder = new LinkedHashMap<K, Long>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Long> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized V get(K key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            accessOrder.remove(key);
            return null;
        }

        // 更新访问顺序
        accessOrder.put(key, entry.getLastAccessTime());
        return (V) entry.getHandler();
    }

    public synchronized V put(K key, V value) {
        CacheEntry oldEntry = cache.get(key);

        // 创建新的缓存条目
        CacheEntry newEntry = new CacheEntry((RequestHandler) value, ttlMs);
        cache.put(key, newEntry);
        accessOrder.put(key, newEntry.getLastAccessTime());

        // 如果超过最大大小，移除最旧的条目
        while (accessOrder.size() > maxSize) {
            Map.Entry<K, Long> eldest = accessOrder.entrySet().iterator().next();
            K eldestKey = eldest.getKey();
            cache.remove(eldestKey);
            accessOrder.remove(eldestKey);
        }

        return oldEntry != null ? (V) oldEntry.getHandler() : null;
    }

    public synchronized V remove(K key) {
        CacheEntry entry = cache.remove(key);
        accessOrder.remove(key);
        return entry != null ? (V) entry.getHandler() : null;
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized void clear() {
        cache.clear();
        accessOrder.clear();
    }

    /**
     * 清理过期的条目
     */
    public synchronized int cleanupExpired() {
        List<K> expiredKeys = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<K, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredKeys.add(entry.getKey());
            }
        }

        for (K key : expiredKeys) {
            cache.remove(key);
            accessOrder.remove(key);
        }

        return expiredKeys.size();
    }
}
