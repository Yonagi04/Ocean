package com.yonagi.ocean.core.router.config.source;

import com.yonagi.ocean.core.router.config.RouteConfig;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 13:53
 */
public interface ConfigSource {

    List<RouteConfig> load();

    void onChange(Runnable callback);
}
