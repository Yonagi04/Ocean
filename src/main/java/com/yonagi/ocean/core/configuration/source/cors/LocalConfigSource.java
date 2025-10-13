package com.yonagi.ocean.core.configuration.source.cors;

import com.yonagi.ocean.core.configuration.CorsConfig;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 14:40
 */
public class LocalConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(LocalConfigSource.class);

    @Override
    public CorsConfig load() {
        boolean enabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cors.enabled", "false"));
        String allowOrigin = LocalConfigLoader.getProperty("server.cors.allow_origin", "*");
        String allowMethods = LocalConfigLoader.getProperty("server.cors.allow_methods", "*");
        String allowHeaders = LocalConfigLoader.getProperty("server.cors.allow_headers", "*");
        String exposeHeaders = LocalConfigLoader.getProperty("server.cors.expose_headers", "*");
        boolean allowCredentials = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cors.allow_credentials", "false"));
        int maxAge = Integer.parseInt(LocalConfigLoader.getProperty("server.cors.max_age", "3600"));

        if (maxAge < 0) {
            log.warn("maxAge is invalid, reset to default value: 3600");
            maxAge = 3600;
        }
        return new CorsConfig.Builder()
                .enabled(enabled)
                .allowOrigin(allowOrigin)
                .allowMethods(allowMethods)
                .allowHeaders(allowHeaders)
                .exposeHeaders(exposeHeaders)
                .allowCredentials(allowCredentials)
                .maxAge(maxAge)
                .build();
    }

    @Override
    public void onChange(Runnable callback) {
        // 本地配置不支持热更新
    }
}
