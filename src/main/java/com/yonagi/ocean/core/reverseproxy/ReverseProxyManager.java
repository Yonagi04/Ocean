package com.yonagi.ocean.core.reverseproxy;

import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.core.reverseproxy.config.source.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 18:30
 */
public class ReverseProxyManager {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyManager.class);

    private static volatile ReverseProxyManager INSTANCE;

    private List<ReverseProxyConfig> reverseProxyConfigs;

    private final Map<String, ReverseProxyHandler> handlerCache = new ConcurrentHashMap<>();

    private final AtomicLong versionCenter = new AtomicLong(0);

    public ReverseProxyManager() {

    }

    public static ReverseProxyManager getInstance() {
        return INSTANCE;
    }

    public static void setInstance(ReverseProxyManager instance) {
        INSTANCE = instance;
    }

    public void refreshReverseProxyConfigs(ConfigManager configManager) {
        try {
            List<ReverseProxyConfig> newConfigs = configManager.load();
            List<ReverseProxyConfig> oldConfigs = reverseProxyConfigs;
            this.reverseProxyConfigs = newConfigs;

            long newVersion = this.versionCenter.incrementAndGet();
            newConfigs.forEach(config -> {
                if (config.getLbConfig() != null) {
                    config.getLbConfig().setVersion(newVersion);
                }
            });

            Set<String> oldUpstreams = extractUpstreams(oldConfigs);
            Set<String> newUpstreams = extractUpstreams(newConfigs);
            if (!oldUpstreams.isEmpty()) {
                oldUpstreams.removeAll(newUpstreams);
            }
            for (String removedUrl : oldUpstreams) {
                HttpClientManager.removeClient(removedUrl);
            }

            for (String addedUrl : newUpstreams) {
                HttpClientManager.getClient(URI.create(addedUrl));
            }

            this.handlerCache.clear();
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

    public ReverseProxyHandler getOrCreateHandler(ReverseProxyConfig config) {
        String key = config.getId();
        return handlerCache.computeIfAbsent(key, k -> new ReverseProxyHandler(config));
    }

    private Set<String> extractUpstreams(List<ReverseProxyConfig> configs) {
        if (configs == null) {
            return Set.of();
        }
        return configs.stream()
                .filter(c -> c.getLbConfig() != null)
                .flatMap(c -> c.getLbConfig().getUpstreams().stream())
                .map(Upstream::getUrl)
                .collect(java.util.stream.Collectors.toSet());
    }

    public void shutdownAll() {
        handlerCache.forEach((k, v) -> v.shutdown());
        handlerCache.clear();
    }
}
