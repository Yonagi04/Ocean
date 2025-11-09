package com.yonagi.ocean.core.configuration.source.reverseproxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.configuration.ReverseProxyConfig;
import com.yonagi.ocean.core.configuration.enums.LoadBalancing;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:25
 */
public class LocalConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(LocalConfigSource.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<ReverseProxyConfig> load() {
        List<ReverseProxyConfig> configs = new ArrayList<>();
        try {
            configs = loadFromClassPath(LocalConfigLoader.getProperty("server.reverse_proxy.config_file_path", "config/reverse_proxy.json"));
            log.info("Loaded {} reverse proxy configurations from local file", configs.size());
        } catch (Exception e) {
            log.error("Failed to load reverse proxy configurations: {}", e.getMessage(), e);
        }
        return configs;
    }

    private List<ReverseProxyConfig> loadFromClassPath(String resourcePath) throws IOException {
        try (InputStream inputStream = LocalConfigSource.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.warn("Reverse proxy configuration file not found in classpath: {}", resourcePath);
                return new ArrayList<>();
            }
            List<ReverseProxyConfigDto> dtos = mapper.readValue(inputStream, new TypeReference<>() {});
            return convertToReverseProxyConfigs(dtos);
        }
    }

    private List<ReverseProxyConfig> convertToReverseProxyConfigs(List<ReverseProxyConfigDto> dtos) {
        List<ReverseProxyConfig> configs = new ArrayList<>();

        for (ReverseProxyConfigDto dto : dtos) {
            try {
                ReverseProxyConfig config = new ReverseProxyConfig.Builder()
                        .enabled(dto.enabled)
                        .id(dto.id)
                        .path(dto.path)
                        .targetUrl(dto.targetUrl)
                        .stripPrefix(dto.stripPrefix)
                        .timeout(dto.timeout)
                        .loadBalancing(dto.loadBalancing)
                        .addHeaders(dto.addHeaders)
                        .build();
                configs.add(config);
            } catch (Exception e) {
                log.error("Failed to load reverse proxy configuration: {}", e.getMessage(), e);
            }
        }

        return configs;
    }

    private static class ReverseProxyConfigDto {
        public boolean enabled;
        public String id;
        public String path;
        public String targetUrl;
        public Boolean stripPrefix;
        public Integer timeout;
        public LoadBalancing loadBalancing;
        public Map<String, String> addHeaders;
    }

    @Override
    public void onChange(Runnable callback) {

    }
}
