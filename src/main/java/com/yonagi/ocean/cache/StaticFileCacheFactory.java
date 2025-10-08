package com.yonagi.ocean.cache;

import com.yonagi.ocean.cache.provider.CacheProvider;
import com.yonagi.ocean.cache.provider.CaffeineCacheProvider;
import com.yonagi.ocean.cache.provider.LRUCacheProvider;
import com.yonagi.ocean.cache.provider.NoCacheProvider;
import com.yonagi.ocean.cache.configuration.CacheConfig;
import com.yonagi.ocean.cache.configuration.source.ConfigSource;
import com.yonagi.ocean.cache.configuration.source.FallbackConfigSource;
import com.yonagi.ocean.cache.configuration.source.LocalConfigSource;
import com.yonagi.ocean.cache.configuration.source.NacosConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/06 09:12
 */
public class StaticFileCacheFactory {

    private static final Logger log = LoggerFactory.getLogger(StaticFileCacheFactory.class);

    private static final AtomicReference<StaticFileCache> REF = new AtomicReference<>();
    private static List<CacheProvider> providers;
    private static ConfigSource configSource;

    private StaticFileCacheFactory() {}

    public static void init() {
        if (REF.get() != null) {
            return;
        }
        synchronized (StaticFileCacheFactory.class) {
            if (REF.get() != null) {
                return;
            }
            providers = Arrays.asList(new LRUCacheProvider(), new CaffeineCacheProvider(), new NoCacheProvider());
            configSource = new FallbackConfigSource(new NacosConfigSource(), new LocalConfigSource());
            refresh();
            configSource.onChange(StaticFileCacheFactory::refresh);
        }
    }

    private static void refresh() {
        final CacheConfig loaded = configSource.load();
        final CacheConfig cfg = loaded != null
                ? loaded
                : CacheConfig.builder().enabled(false).type(CacheConfig.Type.NONE).build();
        StaticFileCache created;
        if (!cfg.isEnabled()) {
            created = new NoCacheProvider().create(cfg);
        } else {
            created = providers.stream()
                    .filter(p -> p.supports(cfg.getType()))
                    .findFirst()
                    .orElse(new NoCacheProvider())
                    .create(cfg);
        }
        REF.set(created);
        log.info("StaticFileCache refreshed to type {} (enabled={})", cfg.getType(), cfg.isEnabled());
        if (CacheConfig.Type.LRU.equals(cfg.getType())) {
            log.info("LRU Config: maxEntries={}, ttlMs={}, policy={}, maxMemoryMb={}, dynamicAdjustment={}, adjustIntervalMs={}",
                    cfg.getLruMaxEntries(), cfg.getLruTtlMs(), cfg.getLruPolicy(), cfg.getLruMaxMemoryMb(),
                    cfg.isLruDynamicAdjustment(), cfg.getLruAdjustIntervalMs());
        } else if (CacheConfig.Type.CAFFEINE.equals(cfg.getType())) {
            log.info("Caffeine Config: expireType={}, ttlMs={}, policy={}, maxEntries={}, maxMemoryMb={}, isSoftValues={}",
                    cfg.getCaffeineExpireType(), cfg.getCaffeineTtlMs(), cfg.getCaffeinePolicy(),
                    cfg.getCaffeineMaxEntries(), cfg.getCaffeineMaxMemoryMb(), cfg.isCaffeineSoftValues());
        }
    }

    public static StaticFileCache getInstance() {
        if (REF.get() == null) {
            init();
        }
        return REF.get();
    }

    // 监听已由 ConfigSource 内部负责
}
