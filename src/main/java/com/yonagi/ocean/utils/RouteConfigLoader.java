package com.yonagi.ocean.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.config.RouteConfig;
import com.yonagi.ocean.core.protocol.HttpMethod;
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
 * @description 路由配置加载器
 * @date 2025/10/08 11:35
 */
public class RouteConfigLoader {
    
    private static final Logger log = LoggerFactory.getLogger(RouteConfigLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private RouteConfigLoader() {}
    
    /**
     * 从配置目录加载路由配置
     * @return 路由配置列表
     */
    public static List<RouteConfig> loadRouteConfigs() {
        List<RouteConfig> routeConfigs = new ArrayList<>();
        try {
            routeConfigs = loadFromClasspath("/config/route.json");
            log.info("Loaded {} route configurations", routeConfigs.size());
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
    private static List<RouteConfig> loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream inputStream = RouteConfigLoader.class.getResourceAsStream(resourcePath)) {
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
                        .enabled(dto.enabled)
                        .method(method)
                        .path(dto.path)
                        .handlerClassName(dto.handler)
                        .contentType(dto.contentType)
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
}
