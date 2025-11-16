package com.yonagi.ocean.core.gzip;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.gzip.config.GzipConfig;
import com.yonagi.ocean.core.gzip.config.source.*;
import com.yonagi.ocean.spi.ConfigRecoveryAction;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 11:38
 */
public class GzipEncoderManager {

    private static final Logger log = LoggerFactory.getLogger(GzipEncoderManager.class);

    private static volatile GzipEncoderManager INSTANCE;
    private static ReentrantLock lock = new ReentrantLock();

    private static final AtomicReference<GzipEncoder> ENCODER_REF = new AtomicReference<>();
    private static ConfigManager configManager;

    private GzipEncoderManager() {}

    public static void init() {
        if (INSTANCE == null) {
            lock.lock();
            try {
                if (INSTANCE == null) {
                    INSTANCE = new GzipEncoderManager();
                }
            } finally {
                lock.unlock();
            }
        }

        if (ENCODER_REF.get() != null) {
            return;
        }

        configManager = new ConfigManager(NacosConfigLoader.getConfigService());
        refresh();
        configManager.onChange(GzipEncoderManager::refresh);
    }

    private static void refresh() {
        final GzipConfig loaded = configManager.load();
        final GzipConfig config = loaded != null
                ? loaded :
                new GzipConfig(false, 1024, 6);
        GzipEncoder encoder = new GzipEncoder(config);
        ENCODER_REF.set(encoder);
        log.info("GzipEncoder refreshed: enabled: {}, minContentLength: {}, compressionLevel: {}",
                config.isEnabled(), config.getMinContentLength(), config.getCompressionLevel());
    }

    public static GzipEncoder getEncoderInstance() {
        if (ENCODER_REF.get() == null) {
            init();
        }
        return ENCODER_REF.get();
    }
}
