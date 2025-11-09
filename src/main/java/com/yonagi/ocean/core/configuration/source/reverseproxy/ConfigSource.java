package com.yonagi.ocean.core.configuration.source.reverseproxy;

import com.yonagi.ocean.core.configuration.ReverseProxyConfig;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:19
 */
public interface ConfigSource {

    List<ReverseProxyConfig> load();

    void onChange(Runnable callback);
}
