package com.yonagi.ocean.core.ratelimiter;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.ratelimiter.config.RateLimitConfig;
import com.yonagi.ocean.core.ratelimiter.config.enums.RateLimitType;
import com.yonagi.ocean.core.ratelimiter.config.source.ConfigSource;
import com.yonagi.ocean.core.ratelimiter.config.source.MutableConfigSource;
import com.yonagi.ocean.core.ratelimiter.config.source.NacosConfigSource;
import com.yonagi.ocean.core.ratelimiter.algorithm.AllwaysAllowRateLimiter;
import com.yonagi.ocean.core.ratelimiter.algorithm.RateLimiter;
import com.yonagi.ocean.core.ratelimiter.algorithm.TokenBucket;
import com.yonagi.ocean.spi.ConfigRecoveryAction;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 10:02
 */
public class RateLimiterManager {

    public static class RateLimiterConfigRecoveryAction implements ConfigRecoveryAction {
        private final MutableConfigSource proxy;
        private final RateLimiterManager globalRateLimiterManager;

        public RateLimiterConfigRecoveryAction(MutableConfigSource proxy, RateLimiterManager globalRateLimiterManager) {
            this.proxy = proxy;
            this.globalRateLimiterManager = globalRateLimiterManager;
        }

        @Override
        public void recover(ConfigService configService) {
            log.info("Nacos reconnected. Executing recovery action for Rate-limiting Configuration");
            NacosConfigSource liveSource = new NacosConfigSource(configService);
            proxy.updateSource(liveSource);
            liveSource.onChange(() -> globalRateLimiterManager.refreshRateLimiter(proxy));

            globalRateLimiterManager.refreshRateLimiter(proxy);
            log.info("Rate-limiting Configuration successfully switched to Nacos primary source");
        }
    }

    private static volatile RateLimiterManager INSTANCE;

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    private final Map<RateLimitType, RateLimitParams> limitParams;

    private List<RateLimitConfig> rateLimitConfigs;

    private static final Logger log = LoggerFactory.getLogger(RateLimiterManager.class);

    public RateLimiterManager() {
        this.limitParams = loadLimitParams();
    }

    public static RateLimiterManager getInstance() {
        return INSTANCE;
    }

    public static void setInstance(RateLimiterManager instance) {
        INSTANCE = instance;
    }

    public void preloadGlobalLimiter() {
        RateLimitParams params = limitParams.get(RateLimitType.GLOBAL_URI);
        if (params == null) {
            return;
        }
        for (RateLimitConfig config : rateLimitConfigs) {
            if (config.isEnabled() && config.getScopes().contains(RateLimitType.GLOBAL_URI)) {
                String globalKey = config.getMethod().name() + ":" + config.getPath();
                limiters.putIfAbsent(globalKey,
                        new TokenBucket(params.getCapacity(), params.getRate())
                );
            }
        }
    }

    public RateLimiter getRateLimiter(String key, RateLimitType type) {
        RateLimitParams params = limitParams.get(type);
        if (params == null) {
            return new AllwaysAllowRateLimiter();
        }
        return limiters.computeIfAbsent(key, k -> new TokenBucket(params.getCapacity(), params.getRate()));
    }

    public List<RateLimitConfig> getConfigs() {
        return rateLimitConfigs;
    }

    private Map<RateLimitType, RateLimitParams> loadLimitParams() {
        Map<RateLimitType, RateLimitParams> map = new HashMap<>();
        map.put(RateLimitType.IP_GLOBAL, new RateLimitParams(
                Integer.parseInt(LocalConfigLoader.getProperty("server.rate_limit.ip_global.capacity", "1000")),
                Double.parseDouble(LocalConfigLoader.getProperty("server.rate_limit.ip_global.rate", "1.667"))
        ));
        map.put(RateLimitType.IP_URI, new RateLimitParams(
                Integer.parseInt(LocalConfigLoader.getProperty("server.rate_limit.ip_uri.capacity", "100")),
                Double.parseDouble(LocalConfigLoader.getProperty("server.rate_limit.ip_uri.rate", "0.1667"))
        ));
        map.put(RateLimitType.GLOBAL_URI, new RateLimitParams(
                Integer.parseInt(LocalConfigLoader.getProperty("server.rate_limit.global_uri.capacity", "5000")),
                Double.parseDouble(LocalConfigLoader.getProperty("server.rate_limit.global_uri.rate", "8.333"))
        ));
        return map;
    }

    public void refreshRateLimiter(ConfigSource configSource) {
        try {
            this.rateLimitConfigs = configSource.load();
            log.info("Rate limiting rules refreshed - Total rules: {}", rateLimitConfigs.size());
        } catch (Exception e) {
            log.error("Failed to refresh rate limiting rules: {}", e.getMessage(), e);
        }
    }

    public void initializeRateLimiter(ConfigSource configSource) {
        try {
            this.rateLimitConfigs = configSource.load();
            log.info("Rate limiting rules initialized - Total rules: {}", rateLimitConfigs.size());
        } catch (Exception e) {
            log.error("Failed to initialize rate limiting rules: {}", e.getMessage(), e);
        }
    }
}
