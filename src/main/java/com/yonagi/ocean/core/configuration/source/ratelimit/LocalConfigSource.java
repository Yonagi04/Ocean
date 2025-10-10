package com.yonagi.ocean.core.configuration.source.ratelimit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.configuration.RateLimitConfig;
import com.yonagi.ocean.core.configuration.enums.RateLimitType;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
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
 * @date 2025/10/10 11:47
 */
public class LocalConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(LocalConfigSource.class);
    private static final ObjectMapper mapper = new ObjectMapper();


    @Override
    public List<RateLimitConfig> load() {
        List<RateLimitConfig> rateLimitConfigs = new ArrayList<>();
        try {
            rateLimitConfigs = loadFromClassPath(LocalConfigLoader.getProperty("server.rate_limit.config_file_path", "config/ratelimit.json"));
            log.info("Loaded {} rate limit configurations from local file", rateLimitConfigs.size());
        } catch (Exception e) {
            log.error("Failed to load rate limit configurations: {}",e.getMessage(), e);
        }
        return rateLimitConfigs;
    }

    private List<RateLimitConfig> loadFromClassPath(String resourcePath) throws IOException {
        try (InputStream inputStream = LocalConfigSource.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.warn("Rate limit configuration file not found in classpath: {}", resourcePath);
                return new ArrayList<>();
            }
            List<RateLimitConfigDto> dtos = mapper.readValue(inputStream, new TypeReference<List<RateLimitConfigDto>>() {});
            return convertToRateLimitConfigs(dtos);
        }
    }

    private List<RateLimitConfig> convertToRateLimitConfigs(List<RateLimitConfigDto> dtos) {
        List<RateLimitConfig> configs = new ArrayList<>();

        for (RateLimitConfigDto dto : dtos) {
            try {
                HttpMethod method = HttpMethod.valueOf(dto.method.toUpperCase());
                RateLimitConfig.Builder builder = new RateLimitConfig.Builder()
                        .enabled(dto.enabled)
                        .method(method)
                        .path(dto.path);
                if (dto.scopes != null && !dto.scopes.isEmpty()) {
                    builder.scopes(dto.scopes);
                }
                RateLimitConfig config = builder.build();
                configs.add(config);
            } catch (Exception e) {
                log.error("Failed to parse rate limit configuration: {} - {}", dto, e.getMessage());
            }
        }
        return configs;
    }

    private static class RateLimitConfigDto {
        public boolean enabled;
        public String method;
        public String path;
        public List<RateLimitType> scopes;

        @Override
        public String toString() {
            return String.format("{enabled=%s, method=%s, path=%s, scope count=%s}", enabled, method, path, scopes.size());
        }
    }

    @Override
    public void onChange(Runnable callback) {
        // 本地配置不支持热更新
    }
}
