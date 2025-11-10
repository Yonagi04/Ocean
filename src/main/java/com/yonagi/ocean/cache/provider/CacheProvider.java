package com.yonagi.ocean.cache.provider;

import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.config.CacheConfig;

public interface CacheProvider {
    boolean supports(CacheConfig.Type type);
    StaticFileCache create(CacheConfig cfg);
}


