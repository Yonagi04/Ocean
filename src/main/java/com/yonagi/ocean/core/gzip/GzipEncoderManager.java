package com.yonagi.ocean.core.gzip;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.configuration.GzipConfig;
import com.yonagi.ocean.core.configuration.source.gzip.*;
import com.yonagi.ocean.spi.ConfigRecoveryAction;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 11:38
 */
public class GzipEncoderManager {

    public static class GzipConfigRecoveryAction implements ConfigRecoveryAction {
        private final MutableConfigSource proxy;
        private final GzipEncoderManager globalGzipEncoderManager;

        public GzipConfigRecoveryAction(MutableConfigSource proxy, GzipEncoderManager globalGzipEncoderManager) {
            this.proxy = proxy;
            this.globalGzipEncoderManager = globalGzipEncoderManager;
        }

        @Override
        public void recover(ConfigService configService) {
            GzipEncoderManager.log.info("Nacos reconnected. Executing recovery action for Gzip Configuration");
            NacosConfigSource liveSource = new NacosConfigSource(configService);
            proxy.updateSource(liveSource);
            liveSource.onChange(GzipEncoderManager::refresh);

            globalGzipEncoderManager.refresh();
            GzipEncoderManager.log.info("Gzip Configuration successfully switched to Nacos primary source");
        }
    }
    private static final Logger log = LoggerFactory.getLogger(GzipEncoderManager.class);

    private static volatile GzipEncoderManager INSTANCE;

    private static final AtomicReference<GzipEncoder> ENCODER_REF = new AtomicReference<>();
    private static ConfigSource configSource;
    private static MutableConfigSource nacosConfigSourceProxy;

    private GzipEncoderManager() {}

    public static void init() {
        // 1. 确保 GzipEncoderManager 自身（工厂）的单例 INSTANCE 被创建 (双重检查锁定)
        if (INSTANCE == null) {
            synchronized (GzipEncoderManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new GzipEncoderManager();
                }
            }
        }

        // 2. 如果 GzipEncoder 实例已被初始化，直接返回
        if (ENCODER_REF.get() != null) {
            return;
        }

        // 3. 配置链初始化（只做一次）
        NacosConfigSource initialNacosSource = new NacosConfigSource(NacosConfigLoader.getConfigService());
        nacosConfigSourceProxy = new MutableConfigSource(initialNacosSource);
        configSource = new FallbackConfigSource(nacosConfigSourceProxy, new LocalConfigSource());

        // 4. 注册 Recovery Action，传入 newly created INSTANCE
        NacosConfigLoader.registerRecoveryAction(new GzipConfigRecoveryAction(nacosConfigSourceProxy, INSTANCE));

        // 5. 首次刷新和监听器注册
        refresh();
        configSource.onChange(GzipEncoderManager::refresh);
    }

    private static void refresh() {
        final GzipConfig loaded = configSource.load();
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
