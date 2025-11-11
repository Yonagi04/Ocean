package com.yonagi.ocean.core.reverseproxy.config.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;
import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
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
                log.warn("Reverse proxy config file not found in classpath: {}", resourcePath);
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
                        .stripPrefix(dto.stripPrefix)
                        .lbConfig(dto.lbConfig)
                        .timeout(dto.timeout)
                        .addHeaders(dto.addHeaders)
                        .build();
                configs.add(config);
            } catch (Exception e) {
                log.error("Failed to load reverse proxy config: {}", e.getMessage(), e);
            }
        }

        return configs;
    }

    private static class ReverseProxyConfigDto {
        public boolean enabled;
        public String id;
        public String path;
        public Boolean stripPrefix;
        public LoadBalancerConfig lbConfig;
        public Integer timeout;
        public Map<String, String> addHeaders;
    }

    @Override
    public void onChange(Runnable callback) {

    }
}
