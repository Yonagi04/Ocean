package com.yonagi.ocean.core.configuration.source.router;

import com.yonagi.ocean.core.configuration.RouteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/12 17:28
 */
public class MutableConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(MutableConfigSource.class);

    private volatile ConfigSource currentSource;

    public MutableConfigSource(ConfigSource configSource) {
        this.currentSource = configSource;
    }

    public void updateSource(ConfigSource newSource) {
        this.currentSource = newSource;
        log.info("Router primary ConfigSource successfully switched to: {}", newSource.getClass().getSimpleName());
    }

    @Override
    public List<RouteConfig> load() {
        return currentSource.load();
    }

    @Override
    public void onChange(Runnable callback) {
        currentSource.onChange(callback);
    }
}
