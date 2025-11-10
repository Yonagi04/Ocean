package com.yonagi.ocean.core.cors;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.cors.config.CorsConfig;
import com.yonagi.ocean.core.cors.config.source.*;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.spi.ConfigRecoveryAction;
import com.yonagi.ocean.utils.NacosConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 15:08
 */
public class CorsManager {

    public static class CorsConfigRecoveryAction implements ConfigRecoveryAction {
        private final MutableConfigSource proxy;
        private final CorsManager globalManager;

        public CorsConfigRecoveryAction(MutableConfigSource proxy, CorsManager globalManager) {
            this.proxy = proxy;
            this.globalManager = globalManager;
        }

        @Override
        public void recover(ConfigService configService) {
            CorsManager.log.info("Nacos reconnected. Executing recovery action for Cors Configuration");
            NacosConfigSource liveSource = new NacosConfigSource(configService);
            proxy.updateSource(liveSource);
            liveSource.onChange(CorsManager::refresh);
            globalManager.refresh();
            CorsManager.log.info("Cors Configuration successfully switched to Nacos primary source");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(CorsManager.class);

    private static volatile CorsManager INSTANCE;
    private static ReentrantLock lock = new ReentrantLock();

    private static final AtomicReference<CorsConfig> REF = new AtomicReference<>();
    private static ConfigSource configSource;
    private static MutableConfigSource nacosConfigSourceProxy;

    private CorsManager() {}

    public static void init() {
        if (INSTANCE == null) {
            lock.lock();
            try {
                if (INSTANCE == null) {
                    INSTANCE = new CorsManager();
                }
            } finally {
                lock.unlock();
            }
        }
        if (REF.get() != null) {
            return;
        }
        NacosConfigSource initialNacosSource = new NacosConfigSource(NacosConfigLoader.getConfigService());
        nacosConfigSourceProxy = new MutableConfigSource(initialNacosSource);
        configSource = new FallbackConfigSource(nacosConfigSourceProxy, new LocalConfigSource());

        NacosConfigLoader.registerRecoveryAction(new CorsConfigRecoveryAction(nacosConfigSourceProxy, INSTANCE));
        refresh();
        configSource.onChange(CorsManager::refresh);
    }

    private static void refresh() {
        final CorsConfig loaded = configSource.load();
        final CorsConfig config = loaded != null
                ? loaded :
                new CorsConfig.Builder()
                        .enabled(false)
                        .allowOrigin("*")
                        .allowMethods("*")
                        .allowHeaders("*")
                        .exposeHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600)
                        .build();
        REF.set(config);
        log.info("Cors Configuration refreshed: enabled: {}, allowOrigin: {}",
                config.isEnabled(), config.getAllowOrigin());
    }

    public static CorsManager getInstance() {
        if (INSTANCE == null) {
            init();
        }
        return INSTANCE;
    }

    public static CorsConfig getCurrentConfig() {
        if (REF.get() == null) {
            init();
        }
        return REF.get();
    }

    public static Map<String, String> handleCors(HttpRequest request) {
        CorsConfig config = getCurrentConfig();
        if (config == null || !config.isEnabled()) {
            return null;
        }

        String origin = request.getHeaders().get("origin");
        if (origin == null) {
            origin = request.getHeaders().get("Origin");
            if (origin == null) {
                return null;
            }
        }
        String allowOrigin = config.getAllowOrigin();
        // TODO: origin白名单机制
        if (!allowOrigin.equals("*") && !allowOrigin.equals(origin)) {
            return null;
        }
        Map<String, String> corsHeaders = new HashMap<>();
        corsHeaders.put("Access-Control-Allow-Origin", allowOrigin);
        corsHeaders.put("Vary", "Origin");
        if (config.isAllowCredentials()) {
            corsHeaders.put("Access-Control-Allow-Credentials", "true");
        }
        if (!config.getExposeHeaders().isEmpty()) {
            corsHeaders.put("Access-Control-Expose-Headers", config.getExposeHeaders());
        }
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            handlePreflight(request, corsHeaders, config);
            corsHeaders.put("__IS_PREFLIGHT__", "true");
            return corsHeaders;
        }
        return corsHeaders;
    }

    private static void handlePreflight(HttpRequest request, Map<String, String> corsHeaders, CorsConfig config) {
        corsHeaders.put("Access-Control-Allow-Methods", config.getAllowMethods());
        corsHeaders.put("Access-Control-Max-Age", String.valueOf(config.getMaxAge()));

        if (!config.getAllowHeaders().isEmpty()) {
            corsHeaders.put("Access-Control-Allow-Headers", config.getAllowHeaders());
        } else {
            String requestedHeaders = request.getHeaders().get("Access-Control-Request-Headers");
            if (requestedHeaders != null) {
                corsHeaders.put("Access-Control-Allow-Headers", requestedHeaders);
            }
        }
    }
}
