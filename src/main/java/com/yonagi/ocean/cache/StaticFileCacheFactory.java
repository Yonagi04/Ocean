package com.yonagi.ocean.cache;

import com.alibaba.nacos.api.config.listener.Listener;
import com.yonagi.ocean.cache.impl.CaffeineFileCacheImpl;
import com.yonagi.ocean.cache.impl.LRUFileCacheImpl;
import com.yonagi.ocean.cache.impl.NoCacheImpl;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Objects;
import java.util.Properties;
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
        String dataId = LocalConfigLoader.getProperty("nacos.data_id");
        String group = LocalConfigLoader.getProperty("nacos.group");
        String cacheType = Objects.requireNonNull(NacosConfigLoader.getConfig(dataId,
                        group,
                        3000))
                .getProperty("server.cache.type");
        if (cacheType == null) {
            log.warn("server.cache.type from Nacos is null, use local config");
            cacheType = LocalConfigLoader.getProperty("server.cache.type");
        }
        String cacheEnabledString = Objects.requireNonNull(NacosConfigLoader.getConfig(dataId,
                        "DEFAULT_GROUP",
                        3000))
                .getProperty("server.cache.enabled");
        if (cacheEnabledString == null) {
            log.warn("server.cache.enabled from Nacos is null, use local config");
            cacheEnabledString = LocalConfigLoader.getProperty("server.cache.enabled");
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
            INSTANCE.startCleaner(Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.lru.cleanup_interval_ms")), 60000));
            boolean enableDynamicAdjustment = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cache.lru.dynamic_adjustment"));
            if (enableDynamicAdjustment) {
                INSTANCE.startAdjuster(Math.max(Long.parseLong(LocalConfigLoader.getProperty("server.cache.lru.dynamic_adjustment_interval_ms")), 60000));
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
        String dataId = LocalConfigLoader.getProperty("nacos.data_id");
        String group = LocalConfigLoader.getProperty("nacos.group");
        NacosConfigLoader.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configContent) {
                Properties props = new Properties();
                try (StringReader reader = new StringReader(configContent)) {
                    props.load(reader);
                } catch (IOException e) {
                    log.error("Nacos Listener failed to load properties from {}", configContent);
                    return;
                }
                boolean newCacheEnabled = Boolean.parseBoolean(props.getProperty("server.cache.enabled"));
                String newCacheType = props.getProperty("server.cache.type");
                log.info("Nacos Listener received new cache config: enabled={}, type={}", newCacheEnabled, newCacheType);
                buildCache();
            }
        });
    }
}
