package com.yonagi.ocean.cache.provider;

import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.impl.CaffeineFileCacheImpl;
import com.yonagi.ocean.cache.config.CacheConfig;

public class CaffeineCacheProvider implements CacheProvider {
    @Override
    public boolean supports(CacheConfig.Type type) { return type == CacheConfig.Type.CAFFEINE; }

    @Override
    public StaticFileCache create(CacheConfig cfg) {
        return new CaffeineFileCacheImpl(cfg);
    }
}


