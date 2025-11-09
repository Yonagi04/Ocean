package com.yonagi.ocean.core.reverseproxy;

import com.alibaba.nacos.api.config.ConfigService;
import com.yonagi.ocean.core.configuration.ReverseProxyConfig;
import com.yonagi.ocean.core.configuration.source.reverseproxy.ConfigSource;
import com.yonagi.ocean.core.configuration.source.reverseproxy.MutableConfigSource;
import com.yonagi.ocean.core.configuration.source.reverseproxy.NacosConfigSource;
import com.yonagi.ocean.spi.ConfigRecoveryAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 18:30
 */
public class ReverseProxyManager {

    public static class ReverseProxyConfigRecoveryAction implements ConfigRecoveryAction {
        private final MutableConfigSource proxy;
        private final ReverseProxyManager globalReverseProxyManager;

        public ReverseProxyConfigRecoveryAction(MutableConfigSource proxy, ReverseProxyManager globalReverseProxyManager) {
            this.proxy = proxy;
            this.globalReverseProxyManager = globalReverseProxyManager;
        }

        @Override
        public void recover(ConfigService configService) {
            log.info("Nacos reconnected. Executing recovery action for Reverse Proxy Configuration");
            NacosConfigSource liveSource = new NacosConfigSource(configService);
            proxy.updateSource(liveSource);
            liveSource.onChange(() -> globalReverseProxyManager.refreshReverseProxyConfigs(proxy));

            globalReverseProxyManager.refreshReverseProxyConfigs(proxy);
            log.info("Reverse Proxy Configuration successfully switched to Nacos primary source");
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyManager.class);

    private static volatile ReverseProxyManager INSTANCE;

    private List<ReverseProxyConfig> reverseProxyConfigs;

    public ReverseProxyManager() {

    }

    public static ReverseProxyManager getInstance() {
        return INSTANCE;
    }

    public static void setInstance(ReverseProxyManager instance) {
        INSTANCE = instance;
    }

    public void refreshReverseProxyConfigs(ConfigSource source) {
        try {
            this.reverseProxyConfigs = source.load();
            log.info("Reverse Proxy rules refreshed - Total rules: {}", reverseProxyConfigs.size());
        } catch (Exception e) {
            log.error("Failed to refresh Reverse Proxy rules: {}", e.getMessage(), e);
        }
    }

    public List<ReverseProxyConfig> getConfigs() {
        return reverseProxyConfigs;
    }

    public List<ReverseProxyConfig> getSortedConfigs() {
        return reverseProxyConfigs.stream()
                .sorted((a, b) -> Integer.compare(b.getPath().length(), a.getPath().length()))
                .toList();
    }
}
