package com.yonagi.ocean.cache.config.source;

import com.yonagi.ocean.cache.config.CacheConfig;

public interface ConfigSource {
    CacheConfig load();
    void onChange(Runnable callback);
}


