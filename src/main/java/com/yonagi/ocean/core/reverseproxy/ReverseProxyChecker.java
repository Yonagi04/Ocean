package com.yonagi.ocean.core.reverseproxy;

import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.core.protocol.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 19:11
 */
public class ReverseProxyChecker {

    private final ReverseProxyManager manager;

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyChecker.class);

    public ReverseProxyChecker(ReverseProxyManager manager) {
        this.manager = manager;
    }

    public ReverseProxyConfig check(HttpRequest request) {
        String uri = request.getUri();
        String path = removeParamsFromUri(uri);
        List<ReverseProxyConfig> allConfigs = manager.getSortedConfigs();

        for (ReverseProxyConfig config : allConfigs) {
            if (!config.isEnabled()) {
                continue;
            }
            if (PathMatcher.match(config.getPath(), path)) {
                log.debug("Request {} matched ReverseProxyConfig ID: {}", path, config.getId());
                return config;
            }
        }
        return null;
    }

    private String removeParamsFromUri(String uri) {
        int paramIndex = uri.indexOf('?');
        if (paramIndex != -1) {
            return uri.substring(0, paramIndex);
        }
        return uri;
    }
}
