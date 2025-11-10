package com.yonagi.ocean.cache.provider;

import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.impl.LRUFileCacheImpl;
import com.yonagi.ocean.cache.config.CacheConfig;

public class LRUCacheProvider implements CacheProvider {
    @Override
    public boolean supports(CacheConfig.Type type) { return type == CacheConfig.Type.LRU; }

    @Override
    public StaticFileCache create(CacheConfig cfg) {
        LRUFileCacheImpl cache = new LRUFileCacheImpl(cfg);
        cache.startCleaner(Math.max(cfg.getCleanerIntervalMs(), 60000L));
        if (cfg.isLruDynamicAdjustment()) {
            cache.startAdjuster(Math.max(cfg.getLruAdjustIntervalMs(), 60000L));
        }
        return cache;
    }
}


