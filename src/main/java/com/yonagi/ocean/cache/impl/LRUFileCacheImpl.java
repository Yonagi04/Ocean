package com.yonagi.ocean.cache.impl;

import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.config.CacheConfig;
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
import java.util.concurrent.locks.ReentrantLock;

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

    private static final ReentrantLock lock = new ReentrantLock();

    public LRUFileCacheImpl() {
        this(CacheConfig.builder().type(CacheConfig.Type.LRU).build());
    }

    public LRUFileCacheImpl(CacheConfig config) {
        this.maxEntries = Math.max(config.getLruMaxEntries(), 1);
        this.ttlMs = Math.max(config.getLruTtlMs(), 60 * 1000);
        this.cache = new LinkedHashMap<String, CachedFile>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedFile> eldest) {
                return size() > maxEntries;
            }
        };
        this.policy = config.getLruPolicy();
        this.maxMemoryBytes = Math.max(config.getLruMaxMemoryMb(), 64);
        this.currentMemoryBytes = 0;
    }


    @Override
    public CachedFile get(File file) throws IOException {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(File file, CachedFile cf) throws IOException {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void remove(File file) throws IOException {
        lock.lock();
        try {
            CachedFile removed = cache.remove(file.getCanonicalPath());
            if (removed != null) {
                currentMemoryBytes -= removed.getContent().length;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            cache.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean contain(String path) {
        lock.lock();
        try {
            return cache.containsKey(path);
        } finally {
            lock.unlock();
        }
    }

    public CachedFile reload(File file) throws IOException {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    @Override
    public void startCleaner(long periodMs) {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
        }
    }

    public synchronized void stopCleaner() {
        if (cleanerService != null) {
            cleanerService.shutdownNow();
            cleanerService = null;
        }
    }

    private void cleanupExpired() {
        lock.lock();
        try {
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
        } finally {
            lock.unlock();
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
