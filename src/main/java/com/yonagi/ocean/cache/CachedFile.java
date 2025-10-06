package com.yonagi.ocean.cache;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 21:28
 */
public class CachedFile {
    byte[] content;
    long lastModified;
    String mimeType;
    long cacheTime;

    public CachedFile(byte[] content, long lastModified, String mimeType, long cacheTime) {
        this.content = content;
        this.lastModified = lastModified;
        this.mimeType = mimeType;
        this.cacheTime = cacheTime;
    }

    public byte[] getContent() {
        return content;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getCacheTime() {
        return cacheTime;
    }
}
