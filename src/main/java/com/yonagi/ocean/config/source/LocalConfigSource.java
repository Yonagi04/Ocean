package com.yonagi.ocean.config.source;

import com.yonagi.ocean.config.CacheConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;

public class LocalConfigSource implements ConfigSource {

    @Override
    public CacheConfig load() {
        CacheConfig.Builder b = CacheConfig.builder();
        boolean enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cache.enabled"));
        String type = String.valueOf(LocalConfigLoader.getProperty("server.cache.type"));
        b.enabled(enabled);
        if ("LRU".equalsIgnoreCase(type)) {
            b.type(CacheConfig.Type.LRU);
        } else if ("CAFFEINE".equalsIgnoreCase(type)) {
            b.type(CacheConfig.Type.CAFFEINE);
        } else {
            b.type(CacheConfig.Type.NONE);
        }

        // cleaner
        String cleaner = LocalConfigLoader.getProperty("server.cache.lru.cleanup_interval_ms");
        if (cleaner != null) {
            b.cleanerIntervalMs(Math.max(Long.parseLong(cleaner), 60000L));
        }

        // LRU
        setIfPresentInt(b, "server.cache.lru.max_entries", v -> b.lruMaxEntries(Math.max(v, 1)));
        setIfPresentLong(b, "server.cache.lru.ttl_ms", v -> b.lruTtlMs(Math.max(v, 60000L)));
        setIfPresent(b, "server.cache.lru.policy", b::lruPolicy);
        setIfPresentLong(b, "server.cache.lru.max_memory_mb", v -> b.lruMaxMemoryMb(Math.max(v, 16)));
        setIfPresentBool(b, "server.cache.lru.dynamic_adjustment", b::lruDynamicAdjustment);
        setIfPresentLong(b, "server.cache.lru.dynamic_adjustment_interval_ms", v -> b.lruAdjustIntervalMs(Math.max(v, 60000L)));

        // Caffeine
        setIfPresent(b, "server.cache.caffeine.expire_type", b::caffeineExpireType);
        setIfPresentLong(b, "server.cache.caffeine.ttl_ms", v -> b.caffeineTtlMs(Math.max(v, 60000L)));
        setIfPresent(b, "server.cache.caffeine.policy", b::caffeinePolicy);
        setIfPresentLong(b, "server.cache.caffeine.max_memory_mb", v -> b.caffeineMaxMemoryMb(Math.max(v, 100)));
        setIfPresentLong(b, "server.cache.caffeine.max_entries", v -> b.caffeineMaxEntries(Math.max(v, 100)));
        setIfPresentBool(b, "server.cache.caffeine.is_soft_values", b::caffeineSoftValues);

        return b.build();
    }

    @Override
    public void onChange(Runnable callback) {
        // 本地配置通常不支持动态变更，留空
    }

    private interface IntSetter { void set(int v); }
    private interface LongSetter { void set(long v); }
    private interface BoolSetter { void set(boolean v); }
    private interface StrSetter { void set(String v); }

    private void setIfPresent(CacheConfig.Builder b, String key, StrSetter setter) {
        String v = LocalConfigLoader.getProperty(key);
        if (v != null) setter.set(v);
    }
    private void setIfPresentInt(CacheConfig.Builder b, String key, IntSetter setter) {
        String v = LocalConfigLoader.getProperty(key);
        if (v != null) setter.set(Integer.parseInt(v));
    }
    private void setIfPresentLong(CacheConfig.Builder b, String key, LongSetter setter) {
        String v = LocalConfigLoader.getProperty(key);
        if (v != null) setter.set(Long.parseLong(v));
    }
    private void setIfPresentBool(CacheConfig.Builder b, String key, BoolSetter setter) {
        String v = LocalConfigLoader.getProperty(key);
        if (v != null) setter.set(Boolean.parseBoolean(v));
    }
}


