package com.yonagi.ocean.cache.configuration.source;

import com.yonagi.ocean.cache.configuration.CacheConfig;

public class FallbackConfigSource implements ConfigSource {
    private final ConfigSource primary;
    private final ConfigSource fallback;

    public FallbackConfigSource(ConfigSource primary, ConfigSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public CacheConfig load() {
        CacheConfig p = primary.load();
        if (p != null) {
            // 即使 disabled 也优先返回主配置
            return p;
        }
        CacheConfig f = fallback.load();
        return f != null ? f : CacheConfig.builder().enabled(false).type(CacheConfig.Type.NONE).build();
    }

    @Override
    public void onChange(Runnable callback) {
        primary.onChange(callback);
    }
}


