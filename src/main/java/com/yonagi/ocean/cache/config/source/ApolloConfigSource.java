package com.yonagi.ocean.cache.config.source;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;
import com.yonagi.ocean.cache.config.CacheConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/14 16:31
 */
public class ApolloConfigSource implements ConfigSource {

    private Runnable callback;

    private String namespace;
    private Boolean enabled;
    private Config config;

    public ApolloConfigSource() {
        this.namespace = LocalConfigLoader.getProperty("server.cache.apollo.namespace", "application");
        this.enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        startPeriodicSync();
    }

    @Override
    public CacheConfig load() {
        if (!enabled) {
            return null;
        }
        config = ConfigService.getConfig(this.namespace);
        if (config == null) {
            return null;
        }
        CacheConfig.Builder b = CacheConfig.builder();

        boolean enabled = Boolean.parseBoolean(config.getProperty("server.cache.enabled", "false"));
        String type = config.getProperty("server.cache.type", "NONE");
        b.enabled(enabled);
        if ("LRU".equalsIgnoreCase(type)) {
            b.type(CacheConfig.Type.LRU);
        } else if ("CAFFEINE".equalsIgnoreCase(type)) {
            b.type(CacheConfig.Type.CAFFEINE);
        } else {
            b.type(CacheConfig.Type.NONE);
        }

        // cleaner interval
        setIfPresentLong(config, "server.cache.lru.cleanup_interval_ms", v -> b.cleanerIntervalMs(Math.max(v, 60000L)));

        // LRU
        setIfPresentInt(config, "server.cache.lru.max_entries", v -> b.lruMaxEntries(Math.max(v, 1)));
        setIfPresentLong(config, "server.cache.lru.ttl_ms", v -> b.lruTtlMs(Math.max(v, 60000L)));
        setIfPresent(config, "server.cache.lru.policy", b::lruPolicy);
        setIfPresentLong(config, "server.cache.lru.max_memory_mb", v -> b.lruMaxMemoryMb(Math.max(v, 16)));
        setIfPresentBool(config, "server.cache.lru.dynamic_adjustment", b::lruDynamicAdjustment);
        setIfPresentLong(config, "server.cache.lru.dynamic_adjustment_interval_ms", v -> b.lruAdjustIntervalMs(Math.max(v, 60000L)));


        // Caffeine
        setIfPresent(config, "server.cache.caffeine.expire_type", b::caffeineExpireType);
        setIfPresentLong(config, "server.cache.caffeine.ttl_ms", v -> b.caffeineTtlMs(Math.max(v, 60000L)));
        setIfPresent(config, "server.cache.caffeine.policy", b::caffeinePolicy);
        setIfPresentLong(config, "server.cache.caffeine.max_memory_mb", v -> b.caffeineMaxMemoryMb(Math.max(v, 100)));
        setIfPresentLong(config, "server.cache.caffeine.max_entries", v -> b.caffeineMaxEntries(Math.max(v, 100)));
        setIfPresentBool(config, "server.cache.caffeine.is_soft_values", b::caffeineSoftValues);

        return b.build();
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        config.addChangeListener(configChangeEvent -> {
            if (callback != null) {
                callback.run();
            }
        });
    }

    private void startPeriodicSync() {
        boolean enableApollo = Boolean.parseBoolean(LocalConfigLoader.getProperty("apollo.enabled", "false"));
        boolean enableSync = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cache.apollo.sync_to_local", "true"));
        if (!enableApollo || !enableSync) {
            return;
        }
        int syncIntervalSeconds = Integer.parseInt(LocalConfigLoader.getProperty("server.cache.apollo.sync_interval_seconds", "7200"));
        String syncLocalPath = LocalConfigLoader.getProperty("server.cache.apollo.sync_local_path");
        // todo: 实现 Apollo 配置同步到本地文件的逻辑
    }

    private interface IntSetter { void set(int v); }
    private interface LongSetter { void set(long v); }
    private interface BoolSetter { void set(boolean v); }
    private interface StrSetter { void set(String v); }

    private void setIfPresent(Config config, String key, StrSetter setter) {
        String v = config.getProperty(key, null);
        if (v != null) {
            setter.set(v);
        }
    }

    private void setIfPresentInt(Config config, String key, IntSetter setter) {
        String v = config.getProperty(key, null);
        if (v != null) {
            try {
                setter.set(Integer.parseInt(v));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void setIfPresentLong(Config config, String key, LongSetter setter) {
        String v = config.getProperty(key, null);
        if (v != null) {
            try {
                setter.set(Long.parseLong(v));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void setIfPresentBool(Config config, String key, BoolSetter setter) {
        String v = config.getProperty(key, null);
        if (v != null) {
            setter.set(Boolean.parseBoolean(v));
        }
    }
}
