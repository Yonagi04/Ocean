package com.yonagi.ocean.core.gzip.config.source;

import com.yonagi.ocean.core.gzip.config.GzipConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 10:52
 */
public class MutableConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(MutableConfigSource.class);

    private volatile ConfigSource currentSource;

    public MutableConfigSource(ConfigSource currentSource) {
        this.currentSource = currentSource;
    }

    public void updateSource(ConfigSource newSource) {
        this.currentSource = newSource;
        log.info("Gzip primary ConfigSource successfully switched to: {}", newSource.getClass().getSimpleName());
    }

    @Override
    public GzipConfig load() {
        return currentSource.load();
    }

    @Override
    public void onChange(Runnable callback) {
        currentSource.onChange(callback);
    }
}
