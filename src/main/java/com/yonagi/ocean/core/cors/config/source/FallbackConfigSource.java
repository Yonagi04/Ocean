package com.yonagi.ocean.core.cors.config.source;

import com.yonagi.ocean.core.cors.config.CorsConfig;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 15:02
 */
public class FallbackConfigSource implements ConfigSource {

    private final ConfigSource primary;

    private final ConfigSource fallback;

    public FallbackConfigSource(ConfigSource primary, ConfigSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public void onChange(Runnable callback) {
        primary.onChange(callback);
    }

    @Override
    public CorsConfig load() {
        CorsConfig p = primary.load();
        if (p != null) {
            return p;
        }
        CorsConfig f = fallback.load();
        return f != null ?
                f : new CorsConfig.Builder()
                .enabled(false)
                .allowOrigin("*")
                .allowMethods("*")
                .allowHeaders("*")
                .exposeHeaders("*")
                .allowCredentials(false)
                .maxAge(3600)
                .build();
    }
}
