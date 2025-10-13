package com.yonagi.ocean.cache.configuration.source;

import com.yonagi.ocean.cache.configuration.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/12 15:45
 */
public class MutableConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(MutableConfigSource.class);

    private volatile ConfigSource currentSource;

    public MutableConfigSource(ConfigSource configSource) {
        this.currentSource = configSource;
    }

    public void updateSource(ConfigSource newSource) {
        this.currentSource = newSource;
        log.info("Cache primary ConfigSource successfully switched to: {}", newSource.getClass().getSimpleName());
    }

    @Override
    public CacheConfig load() {
        return currentSource.load();
    }

    @Override
    public void onChange(Runnable callback) {
        currentSource.onChange(callback);
    }
}
