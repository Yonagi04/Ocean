package com.yonagi.ocean.cache.configuration.source;

import com.yonagi.ocean.cache.configuration.CacheConfig;

public interface ConfigSource {
    CacheConfig load();
    void onChange(Runnable callback);
}


