package com.yonagi.ocean.core.configuration.source.gzip;

import com.yonagi.ocean.core.configuration.GzipConfig;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 10:52
 */
public class FallbackConfigSource implements ConfigSource {

    private final ConfigSource primary;

    private final ConfigSource fallback;

    public FallbackConfigSource(ConfigSource primary, ConfigSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public GzipConfig load() {
        GzipConfig p = primary.load();
        if (p != null) {
            return p;
        }
        GzipConfig f = fallback.load();
        if (f != null) {
            return f;
        }
        return new GzipConfig(false, 1024, 6);
    }

    @Override
    public void onChange(Runnable callback) {
        primary.onChange(callback);
    }
}
