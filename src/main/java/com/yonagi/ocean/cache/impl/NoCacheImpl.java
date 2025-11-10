package com.yonagi.ocean.cache.impl;

import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.core.protocol.enums.ContentType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 21:50
 */
public class NoCacheImpl implements StaticFileCache {

    @Override
    public CachedFile get(File file) throws IOException {
        byte[] data = Files.readAllBytes(file.toPath());
        return new CachedFile(
                data,
                file.lastModified(),
                ContentType.fromName(file.getName()).getValue(),
                System.currentTimeMillis()
        );
    }

    @Override
    public void put(File file, CachedFile cf) throws IOException {

    }

    @Override
    public void remove(File file) throws IOException {

    }

    @Override
    public void startCleaner(long intervalMs) {
        StaticFileCache.super.startCleaner(intervalMs);
    }

    @Override
    public boolean contain(String path) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public void startAdjuster(long intervalMs) {
        StaticFileCache.super.startAdjuster(intervalMs);
    }
}
