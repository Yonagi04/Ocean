package com.yonagi.ocean.cache.provider;

import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.impl.NoCacheImpl;
import com.yonagi.ocean.cache.config.CacheConfig;

public class NoCacheProvider implements CacheProvider {
    @Override
    public boolean supports(CacheConfig.Type type) { return type == CacheConfig.Type.NONE; }

    @Override
    public StaticFileCache create(CacheConfig cfg) {
        return new NoCacheImpl();
    }
}


