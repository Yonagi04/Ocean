package com.yonagi.ocean.core.configuration.source.reverseproxy;

import com.yonagi.ocean.core.configuration.ReverseProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:25
 */
public class MutableConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(MutableConfigSource.class);

    private volatile ConfigSource currentSource;

    public MutableConfigSource(ConfigSource configSource) {
        this.currentSource = configSource;
    }

    public void updateSource(ConfigSource newSource) {
        this.currentSource = newSource;
        log.info("ReverseProxy primary ConfigSource successfully switched to: {}", newSource.getClass().getSimpleName());
    }

    @Override
    public List<ReverseProxyConfig> load() {
        return currentSource.load();
    }

    @Override
    public void onChange(Runnable callback) {
        currentSource.onChange(callback);
    }
}
