package com.yonagi.ocean.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.config.CacheConfig;
import com.yonagi.ocean.core.protocol.enums.ContentType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/06 09:17
 */
public class CaffeineFileCacheImpl implements StaticFileCache {

    private final Cache<String, CachedFile> cache;

    public CaffeineFileCacheImpl() {
        this(buildConfigFromDefaults());
    }

    public CaffeineFileCacheImpl(CacheConfig config) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        if ("ACCESS".equalsIgnoreCase(config.getCaffeineExpireType())) {
            builder.expireAfterAccess(Math.max(config.getCaffeineTtlMs(), 60000L), TimeUnit.MILLISECONDS);
        } else {
            builder.expireAfterWrite(Math.max(config.getCaffeineTtlMs(), 60000L), TimeUnit.MILLISECONDS);
        }

        if ("MEMORY".equalsIgnoreCase(config.getCaffeinePolicy())) {
            builder.maximumWeight(Math.max(config.getCaffeineMaxMemoryMb(), 100))
                    .weigher((String key, CachedFile value) -> value.getContent().length / 1024);
        } else {
            builder.maximumSize(Math.max(config.getCaffeineMaxEntries(), 100));
        }

        if (config.isCaffeineSoftValues()) {
            builder.softValues();
        }
        builder.recordStats();
        this.cache = builder.build();
    }

    private static CacheConfig buildConfigFromDefaults() {
        return CacheConfig.builder().type(CacheConfig.Type.CAFFEINE).build();
    }

    @Override
    public CachedFile get(File file) throws IOException {
        String path = file.getCanonicalPath();
        CachedFile cf = cache.getIfPresent(path);
        if (cf != null) {
            if (file.lastModified() != cf.getLastModified()) {
                cache.invalidate(path);
                return loadFile(file);
            }
            return cf;
        }
        return loadFile(file);
    }

    private CachedFile loadFile(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        CachedFile newCached = new CachedFile(
                data,
                file.lastModified(),
                ContentType.fromName(file.getName()).getValue(),
                System.currentTimeMillis()
        );
        cache.put(file.getCanonicalPath(), newCached);
        return newCached;
    }

    @Override
    public void put(File file, CachedFile cf) throws IOException {
        String path = file.getCanonicalPath();
        cache.put(path, cf);
    }

    @Override
    public void remove(File file) throws IOException {
        String path = file.getCanonicalPath();
        cache.invalidate(path);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public boolean contain(String path) {
        return cache.getIfPresent(path) != null;
    }

    @Override
    public void startCleaner(long intervalMs) {
        StaticFileCache.super.startCleaner(intervalMs);
    }

    @Override
    public void startAdjuster(long intervalMs) {
        StaticFileCache.super.startAdjuster(intervalMs);
    }
}
