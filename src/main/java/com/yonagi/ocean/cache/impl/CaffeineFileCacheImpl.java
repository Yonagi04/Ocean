package com.yonagi.ocean.cache.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;

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
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        if ("ACCESS".equalsIgnoreCase(LocalConfigLoader.getProperty("server.cache.caffeine.expire_type"))) {
            builder.expireAfterAccess(Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.caffeine.ttl_ms")), 60000L), TimeUnit.MILLISECONDS);
        } else {
            builder.expireAfterWrite(Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.caffeine.ttl_ms")), 60000L), TimeUnit.MILLISECONDS);
        }

        if ("MEMORY".equalsIgnoreCase(LocalConfigLoader.getProperty("server.cache.caffeine.policy"))) {
            // weight in KB
            builder.maximumWeight(Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.caffeine.max_memory_mb")), 100))
                    .weigher((String key, CachedFile value) -> value.getContent().length / 1024);
        } else {
            builder.maximumSize(Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.caffeine.max_entries")), 100));
        }

        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cache.caffeine.is_soft_values"))) {
            builder.softValues();
        }
        builder.recordStats();
        this.cache = builder.build();
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
                MimeTypeUtil.getMimeType(file.getName()),
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
