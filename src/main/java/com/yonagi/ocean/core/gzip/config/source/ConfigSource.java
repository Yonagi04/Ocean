package com.yonagi.ocean.core.gzip.config.source;

import com.yonagi.ocean.core.gzip.config.GzipConfig;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 10:49
 */
public interface ConfigSource {
    GzipConfig load();
    void onChange(Runnable callback);
}
