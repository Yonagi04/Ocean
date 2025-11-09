package com.yonagi.ocean.core.configuration.source.reverseproxy;

import com.yonagi.ocean.core.configuration.ReverseProxyConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:20
 */
public class FallbackConfigSource implements ConfigSource {

    private final ConfigSource primary;

    private final ConfigSource fallback;

    public FallbackConfigSource(ConfigSource primary, ConfigSource fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public List<ReverseProxyConfig> load() {
        List<ReverseProxyConfig> p = primary.load();
        if (p != null) {
            return p;
        }
        List<ReverseProxyConfig> f = fallback.load();
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
