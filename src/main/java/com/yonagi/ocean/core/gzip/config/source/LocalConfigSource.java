package com.yonagi.ocean.core.gzip.config.source;

import com.yonagi.ocean.core.gzip.config.GzipConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 10:52
 */
public class LocalConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(LocalConfigSource.class);
    private static final String DEFAULT_MIN_CONTENT_LENGTH = "1024";
    private static final String DEFAULT_COMPRESSION_LEVEL = "6";

    @Override
    public GzipConfig load() {
        boolean enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.gzip.enabled", "false"));
        int minContentLength = Integer.parseInt(LocalConfigLoader.getProperty("server.gzip.min_content_length", DEFAULT_MIN_CONTENT_LENGTH));
        int compressionLevel = Integer.parseInt(LocalConfigLoader.getProperty("server.gzip.compression_level", DEFAULT_COMPRESSION_LEVEL));

        if (minContentLength < 0) {
            log.warn("minContentLength is invalid. Reset to default value: {}", DEFAULT_MIN_CONTENT_LENGTH);
            minContentLength = Integer.parseInt(DEFAULT_MIN_CONTENT_LENGTH);
        }
        if (compressionLevel < 0 || compressionLevel > 9) {
            log.warn("compressionLevel is invalid, it must to between 1 to 9. Reset to default value: {}", DEFAULT_COMPRESSION_LEVEL);
            compressionLevel = Integer.parseInt(DEFAULT_COMPRESSION_LEVEL);
        }
        return new GzipConfig(enabled, minContentLength, compressionLevel);
    }

    @Override
    public void onChange(Runnable callback) {
        // 本地配置不支持热更新
    }
}
