package com.yonagi.ocean.core.gzip.config;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 10:48
 */
public final class GzipConfig {

    private final boolean enabled;
    private final int minContentLength;
    private final int compressionLevel;

    public GzipConfig(final boolean enabled, final int minContentLength, final int compressionLevel) {
        this.enabled = enabled;
        this.compressionLevel = compressionLevel;
        this.minContentLength = minContentLength;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMinContentLength() {
        return minContentLength;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }
}
