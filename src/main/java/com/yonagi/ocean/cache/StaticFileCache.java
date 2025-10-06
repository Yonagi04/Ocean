package com.yonagi.ocean.cache;

import java.io.File;
import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 21:26
 */
public interface StaticFileCache {

    /** Get the cached file. If not present, read from disk and return.
     * After reading from disk, if the cache is enabled, it should be put into the cache.
     * Do not return null.**/
    CachedFile get(File file) throws IOException;

    /** Put the file into the cache.
     * If the cache is disabled, do nothing. **/
    void put(File file, CachedFile cf) throws IOException;

    /** Remove the file from the cache.
     * If the cache is disabled, do nothing. **/
    void remove(File file) throws IOException;

    void clear();

    default void startCleaner(long intervalMs) {
        // Default implementation does nothing
    }

    boolean contain(String path);

    default void startAdjuster(long intervalMs) {
        // Default implementation does nothing
    }
}
