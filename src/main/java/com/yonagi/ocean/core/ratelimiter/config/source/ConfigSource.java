package com.yonagi.ocean.core.ratelimiter.config.source;

import com.yonagi.ocean.core.ratelimiter.config.RateLimitConfig;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 11:38
 */
public interface ConfigSource {

    List<RateLimitConfig> load();

    void onChange(Runnable callback);
}
