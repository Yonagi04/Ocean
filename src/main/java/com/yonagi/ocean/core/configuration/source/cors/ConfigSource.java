package com.yonagi.ocean.core.configuration.source.cors;

import com.yonagi.ocean.core.configuration.CorsConfig;

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
