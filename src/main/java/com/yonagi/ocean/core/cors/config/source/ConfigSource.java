package com.yonagi.ocean.core.cors.config.source;

import com.yonagi.ocean.core.cors.config.CorsConfig;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 14:38
 */
public interface ConfigSource {
    CorsConfig load();
    void onChange(Runnable callback);
}
