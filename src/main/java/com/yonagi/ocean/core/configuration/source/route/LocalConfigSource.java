package com.yonagi.ocean.core.configuration.source.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.configuration.RouteConfig;
import com.yonagi.ocean.core.protocol.HttpMethod;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 13:53
 */
public class LocalConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(LocalConfigSource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<RouteConfig> load() {
        List<RouteConfig> routeConfigs = new ArrayList<>();
        try {
            routeConfigs = loadFromClasspath(LocalConfigLoader.getProperty("server.router.config_file_path", "/config/route.json"));
            log.info("Loaded {} route configurations from local configuration", routeConfigs.size());
            for (RouteConfig config : routeConfigs) {
                log.info("Route: {} {} -> {} (enabled: {})",
                        config.getMethod(), config.getPath(), config.getHandlerClassName(), config.isEnabled());
            }

        } catch (Exception e) {
            log.error("Failed to load route configuration: {}", e.getMessage(), e);
        }
        return routeConfigs;
    }

    /**
     * 从classpath加载路由配置
     */
    private List<RouteConfig> loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream inputStream = LocalConfigSource.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.warn("Route configuration file not found in classpath: {}", resourcePath);
                return new ArrayList<>();
            }
            List<RouteConfigDto> dtos = objectMapper.readValue(inputStream, new TypeReference<List<RouteConfigDto>>() {});
            return convertToRouteConfigs(dtos);
        }
    }

    /**
     * 将DTO转换为RouteConfig对象
     */
    private static List<RouteConfig> convertToRouteConfigs(List<RouteConfigDto> dtos) {
        List<RouteConfig> configs = new ArrayList<>();

        for (RouteConfigDto dto : dtos) {
            try {
                HttpMethod method = HttpMethod.valueOf(dto.method.toUpperCase());
                RouteConfig config = new RouteConfig.Builder()
                        .withEnabled(dto.enabled)
                        .withMethod(method)
                        .withPath(dto.path)
                        .withHandlerClassName(dto.handler)
                        .withContentType(dto.contentType)
                        .build();
                configs.add(config);
            } catch (Exception e) {
                log.error("Failed to parse route configuration: {} - {}", dto, e.getMessage());
            }
        }

        return configs;
    }

    /**
     * 路由配置DTO类，用于JSON反序列化
     */
    private static class RouteConfigDto {
        public String method;
        public String path;
        public String handler;
        public String contentType;
        public boolean enabled;

        @Override
        public String toString() {
            return String.format("RouteConfigDto{method='%s', path='%s', handler='%s', contentType='%s', enabled=%s}",
                    method, path, handler, contentType, enabled);
        }
    }

    @Override
    public void onChange(Runnable callback) {
        // 本地配置不支持热更新
    }
}
