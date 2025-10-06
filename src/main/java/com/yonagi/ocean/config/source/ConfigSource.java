package com.yonagi.ocean.config.source;

import com.yonagi.ocean.config.CacheConfig;

public interface ConfigSource {
    CacheConfig load();
    void onChange(Runnable callback);
}


