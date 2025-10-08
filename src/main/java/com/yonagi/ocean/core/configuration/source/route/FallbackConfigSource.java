package com.yonagi.ocean.core.configuration.source.route;

import com.yonagi.ocean.core.configuration.RouteConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 13:54
 */
public class FallbackConfigSource implements ConfigSource {
    private final ConfigSource primary;
    private final ConfigSource fallback;

    public FallbackConfigSource(ConfigSource primary, ConfigSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public List<RouteConfig> load() {
        List<RouteConfig> p = primary.load();
        if (p != null) {
            return p;
        }
        List<RouteConfig> f = fallback.load();
        if (f != null) {
            return f;
        }
        return new ArrayList<>();
    }

    @Override
    public void onChange(Runnable callback) {
        primary.onChange(callback);
    }
}
