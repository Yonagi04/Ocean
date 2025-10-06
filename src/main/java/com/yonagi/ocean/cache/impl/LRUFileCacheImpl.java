package com.yonagi.ocean.cache.impl;

import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 21:29
 */
public class LRUFileCacheImpl implements StaticFileCache {
    private final Map<String, CachedFile> cache;
    private final Integer maxEntries;
    private final Long ttlMs;
    private long maxMemoryBytes;
    private long currentMemoryBytes;
    private final String policy;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    private ScheduledExecutorService cleanerService;
    private ScheduledExecutorService adjusterService;

    private static final Logger log = LoggerFactory.getLogger(LRUFileCacheImpl.class);

    public LRUFileCacheImpl() {
        this.maxEntries = Math.max(Integer.parseInt(LocalConfigLoader.getProperty("server.cache.lru.max_entries")), 1);
        this.ttlMs = Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.lru.ttl_ms")), 60 * 1000);
        this.cache = new LinkedHashMap<String, CachedFile>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedFile> eldest) {
                return size() > maxEntries;
            }
        };
        this.policy = LocalConfigLoader.getProperty("server.cache.lru.policy");
        this.maxMemoryBytes = Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.lru.max_memory_mb")), 64);
        this.currentMemoryBytes = 0;
    }


    @Override
    public synchronized CachedFile get(File file) throws IOException {
        String path = file.getCanonicalPath();
        long now = System.currentTimeMillis();

        CachedFile cached = cache.get(path);
        if (cached != null) {
            long fileLastModified = file.lastModified();
            if (fileLastModified != cached.getLastModified()) {
                cache.remove(path);
                missCount.incrementAndGet();
            } else if (ttlMs > 0 && (now - cached.getCacheTime()) > ttlMs) {
                cache.remove(path);
                missCount.incrementAndGet();
            } else {
                hitCount.incrementAndGet();
                return cached;
            }
        } else {
            missCount.incrementAndGet();
        }
        byte[] data = Files.readAllBytes(file.toPath());
        CachedFile newCached = new CachedFile(
                data,
                file.lastModified(),
                MimeTypeUtil.getMimeType(file.getName()),
                now
        );
        cache.put(path, newCached);
        return newCached;
    }

    @Override
    public synchronized void put(File file, CachedFile cf) throws IOException {
        long size = cf.getContent().length;
        if ("MEMORY".equalsIgnoreCase(policy)) {
            while (currentMemoryBytes + size > maxMemoryBytes && !cache.isEmpty()) {
                Map.Entry<String, CachedFile> eldest = cache.entrySet().iterator().next();
                remove(new File(eldest.getKey()));
            }
        }
        String path = file.getCanonicalPath();
        cache.put(path, cf);
        currentMemoryBytes += size;
    }

    @Override
    public void remove(File file) throws IOException {
        CachedFile removed = cache.remove(file.getCanonicalPath());
        if (removed != null) {
            currentMemoryBytes -= removed.getContent().length;
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public synchronized boolean contain(String path) {
        return cache.containsKey(path);
    }

    public synchronized CachedFile reload(File file) throws IOException {
        String path = file.getCanonicalPath();
        byte[] data = Files.readAllBytes(file.toPath());
        CachedFile newCached = new CachedFile(
                data,
                file.lastModified(),
                MimeTypeUtil.getMimeType(file.getName()),
                System.currentTimeMillis()
        );
        cache.put(path, newCached);
        return newCached;
    }

    public synchronized int size() {
        return cache.size();
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    @Override
    public synchronized void startCleaner(long periodMs) {
        if (ttlMs < 0) {
            return;
        }
        if (cleanerService != null && !cleanerService.isShutdown()) {
            return;
        }
        cleanerService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StaticFileCacheLRU-Cleaner");
            t.setDaemon(true);
            return t;
        });
        cleanerService.scheduleAtFixedRate(this::cleanupExpired, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stopCleaner() {
        if (cleanerService != null) {
            cleanerService.shutdownNow();
            cleanerService = null;
        }
    }

    private synchronized void cleanupExpired() {
        if (ttlMs < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CachedFile>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedFile> next = iterator.next();
            CachedFile cf = next.getValue();
            if (now - cf.getCacheTime() > ttlMs) {
                iterator.remove();
            }
        }
    }

    @Override
    public void startAdjuster(long periodMs) {
        if (!"MEMORY".equalsIgnoreCase(policy)) {
            log.warn("Dynamic resize is only supported with MEMORY policy.");
            return;
        }
        if (adjusterService != null && !adjusterService.isShutdown()) {
            return;
        }
        adjusterService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StaticFileCacheLRU-Adjuster");
            t.setDaemon(true);
            return t;
        });
        adjusterService.scheduleAtFixedRate(this::adjustCacheSize, periodMs, periodMs, TimeUnit.MILLISECONDS);
        log.info("LRU Cache Adjuster started with interval {} ms.", periodMs);
    }

    public synchronized void adjustCacheSize() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryUsage = (double) usedMemory / maxMemory;

        double hitRate = (hitCount.get() + missCount.get() == 0) ? 0 : (double) hitCount.get() / (hitCount.get() + missCount.get());

        if (memoryUsage > 0.8) {
            maxMemoryBytes = Math.max((long) ((long) maxMemoryBytes * 0.8), 16 * 1024 * 1024);
            log.warn("Memory usage high ({}%). Reducing cache size to {} MB.", String.format("%.2f", memoryUsage * 100), maxMemoryBytes / (1024 * 1024));
        } else if (memoryUsage < 0.5 && hitRate > 0.7) {
            maxMemoryBytes = (long) ((long) maxMemoryBytes * 1.2);
            log.info("Memory usage low ({}%) and good hit rate ({}%). Increasing cache size to {} MB.", String.format("%.2f", memoryUsage * 100), String.format("%.2f", hitRate * 100), maxMemoryBytes / (1024 * 1024));
        }
    }

    public void stopAdjuster() {
        if (adjusterService != null) {
            adjusterService.shutdownNow();
            adjusterService = null;
        }
    }
}
