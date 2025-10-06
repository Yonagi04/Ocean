package com.yonagi.ocean.cache;

import com.alibaba.nacos.api.config.listener.Listener;
import com.yonagi.ocean.cache.impl.CaffeineFileCacheImpl;
import com.yonagi.ocean.cache.impl.LRUFileCacheImpl;
import com.yonagi.ocean.cache.impl.NoCacheImpl;
import com.yonagi.ocean.utils.ConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/06 09:12
 */
public class StaticFileCacheFactory {

    private static final Logger log = LoggerFactory.getLogger(StaticFileCacheFactory.class);

    private static StaticFileCache INSTANCE;

    private StaticFileCacheFactory() {}

    public static void init() {
        if (INSTANCE != null) {
            return;
        }
        synchronized (StaticFileCacheFactory.class) {
            if (INSTANCE != null) {
                return;
            }
            buildCache();
            addNacosListener();
        }
    }

    private static void buildCache() {
        String cacheType = NacosConfigLoader.getConfig("server.cache.type", "DEFAULT_GROUP", 3000);
        if (cacheType == null) {
            log.warn("Nacos server.cache.type is null, use local config");
            cacheType = ConfigLoader.getProperty("server.cache.type");
        }
        String cacheEnabledString = NacosConfigLoader.getConfig("server.cache.enabled", "DEFAULT_GROUP", 3000);
        if (cacheEnabledString == null) {
            log.warn("Nacos server.cache.enabled is null, use local config");
            cacheEnabledString = ConfigLoader.getProperty("server.cache.enabled");
        }
        boolean cacheEnabled = Boolean.parseBoolean(cacheEnabledString);
        if (!cacheEnabled) {
            log.info("Server cache disabled");
            INSTANCE = new NoCacheImpl();
            return;
        }
        if ("LRU".equalsIgnoreCase(cacheType)) {
            log.info("LRU cache enabled");
            INSTANCE = new LRUFileCacheImpl();
            INSTANCE.startCleaner(Math.max(Long.parseLong(ConfigLoader.getProperty("server.cache.lru.cleanup_interval_ms")), 60000));
            boolean enableDynamicAdjustment = Boolean.parseBoolean(ConfigLoader.getProperty("server.cache.lru.dynamic_adjustment"));
            if (enableDynamicAdjustment) {
                INSTANCE.startAdjuster(Math.max(Long.parseLong(ConfigLoader.getProperty("server.cache.lru.dynamic_adjustment_interval_ms")), 60000));
            }
        } else if ("Caffeine".equalsIgnoreCase(cacheType)) {
            log.info("Caffeine cache enabled");
            INSTANCE = new CaffeineFileCacheImpl();
        } else {
            log.info("No cache enabled");
            INSTANCE = new NoCacheImpl();
        }
    }

    public static StaticFileCache getInstance() {
        if (INSTANCE == null) {
            init();
        }
        return INSTANCE;
    }

    private static void addNacosListener() {
        NacosConfigLoader.addListener("server.cache.type", "DEFAULT_GROUP", new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                log.info("Cache type updated to: {}", configInfo);
                synchronized (StaticFileCacheFactory.class) {
                    buildCache();
                }
            }
        });
    }
}
