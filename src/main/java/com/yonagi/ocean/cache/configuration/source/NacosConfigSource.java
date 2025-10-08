package com.yonagi.ocean.cache.configuration.source;

import com.alibaba.nacos.api.config.listener.Listener;
import com.yonagi.ocean.cache.configuration.CacheConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;

import java.util.Properties;
import java.util.concurrent.Executor;

public class NacosConfigSource implements ConfigSource {
    private Runnable callback;

    public NacosConfigSource() {
        NacosConfigLoader.init();
    }

    @Override
    public CacheConfig load() {
        String dataId = LocalConfigLoader.getProperty("server.cache.nacos.data_id");
        String group = LocalConfigLoader.getProperty("server.cache.nacos.group");
        int timeoutMs = Integer.parseInt(LocalConfigLoader.getProperty("nacos.timeout_ms", "3000"));
        Properties props = NacosConfigLoader.getPropertiesConfig(dataId, group, timeoutMs);

        CacheConfig.Builder b = CacheConfig.builder();
        if (props == null) {
            // 返回 null 触发 Fallback 使用本地配置
            return null;
        }

        boolean enabled = Boolean.parseBoolean(props.getProperty("server.cache.enabled", "false"));
        String type = props.getProperty("server.cache.type", "NONE");
        b.enabled(enabled);
        if ("LRU".equalsIgnoreCase(type)) {
            b.type(CacheConfig.Type.LRU);
        } else if ("CAFFEINE".equalsIgnoreCase(type)) {
            b.type(CacheConfig.Type.CAFFEINE);
        } else {
            b.type(CacheConfig.Type.NONE);
        }

        // cleaner interval（与现有 LRU cleaner 复用）
        setIfPresentLong(props, "server.cache.lru.cleanup_interval_ms", v -> b.cleanerIntervalMs(Math.max(v, 60000L)));

        // LRU
        setIfPresentInt(props, "server.cache.lru.max_entries", v -> b.lruMaxEntries(Math.max(v, 1)));
        setIfPresentLong(props, "server.cache.lru.ttl_ms", v -> b.lruTtlMs(Math.max(v, 60000L)));
        setIfPresent(props, "server.cache.lru.policy", b::lruPolicy);
        setIfPresentLong(props, "server.cache.lru.max_memory_mb", v -> b.lruMaxMemoryMb(Math.max(v, 16)));
        setIfPresentBool(props, "server.cache.lru.dynamic_adjustment", b::lruDynamicAdjustment);
        setIfPresentLong(props, "server.cache.lru.dynamic_adjustment_interval_ms", v -> b.lruAdjustIntervalMs(Math.max(v, 60000L)));

        // Caffeine
        setIfPresent(props, "server.cache.caffeine.expire_type", b::caffeineExpireType);
        setIfPresentLong(props, "server.cache.caffeine.ttl_ms", v -> b.caffeineTtlMs(Math.max(v, 60000L)));
        setIfPresent(props, "server.cache.caffeine.policy", b::caffeinePolicy);
        setIfPresentLong(props, "server.cache.caffeine.max_memory_mb", v -> b.caffeineMaxMemoryMb(Math.max(v, 100)));
        setIfPresentLong(props, "server.cache.caffeine.max_entries", v -> b.caffeineMaxEntries(Math.max(v, 100)));
        setIfPresentBool(props, "server.cache.caffeine.is_soft_values", b::caffeineSoftValues);

        return b.build();
    }

    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
        String dataId = LocalConfigLoader.getProperty("server.cache.nacos.data_id");
        String group = LocalConfigLoader.getProperty("server.cache.nacos.group");
        NacosConfigLoader.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() { return null; }
            @Override
            public void receiveConfigInfo(String configContent) {
                if (callback != null) {
                    callback.run();
                }
            }
        });
    }

    private interface IntSetter { void set(int v); }
    private interface LongSetter { void set(long v); }
    private interface BoolSetter { void set(boolean v); }
    private interface StrSetter { void set(String v); }

    private void setIfPresent(Properties p, String key, StrSetter setter) {
        String v = p.getProperty(key);
        if (v != null) setter.set(v);
    }
    private void setIfPresentInt(Properties p, String key, IntSetter setter) {
        String v = p.getProperty(key);
        if (v != null) {
            setter.set(Integer.parseInt(v));
        }
    }
    private void setIfPresentLong(Properties p, String key, LongSetter setter) {
        String v = p.getProperty(key);
        if (v != null) {
            setter.set(Long.parseLong(v));
        }
    }
    private void setIfPresentBool(Properties p, String key, BoolSetter setter) {
        String v = p.getProperty(key);
        if (v != null) {
            setter.set(Boolean.parseBoolean(v));
        }
    }
}


