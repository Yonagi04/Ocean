package com.yonagi.ocean.core.ratelimiter;

import com.yonagi.ocean.core.configuration.RateLimitConfig;
import com.yonagi.ocean.core.configuration.enums.RateLimitType;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.ratelimiter.algorithm.RateLimiter;
import com.yonagi.ocean.utils.IpExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 15:12
 */
public class RateLimiterChecker {

    private final RateLimiterManager manager;

    private final IpExtractor ipExtractor = new IpExtractor();

    private static final Logger log = LoggerFactory.getLogger(RateLimiterChecker.class);

    public RateLimiterChecker(RateLimiterManager manager) {
        this.manager = manager;
    }

    public boolean check(HttpRequest request) {
        String uri = request.getUri();
        HttpMethod method = request.getMethod();
        String clientIp = ipExtractor.getClientIp(request);

        List<RateLimitConfig> matchedConfigs = findMatchingConfigs(method, uri);
        for (RateLimitConfig config : matchedConfigs) {
            for (RateLimitType type : config.getScopes()) {
                String key = buildRateLimitKey(method, uri, clientIp, type);

                RateLimiter limiter = manager.getRateLimiter(key, type);
                if (!limiter.tryAcquire()) {
                    log.warn("Request {} {} from IP {} blocker by {} policy",
                            method, uri, clientIp, type.name());
                    return false;
                }
            }
        }
        return true;
    }

    private String buildRateLimitKey(HttpMethod method, String uri, String clientIp, RateLimitType type) {
        final String SEPARATOR = ":";

        if (type == RateLimitType.GLOBAL_URI) {
            return method.name() + SEPARATOR + uri;
        } else if (type == RateLimitType.IP_URI) {
            return clientIp + SEPARATOR + method.name() + SEPARATOR + uri;
        } else if (type == RateLimitType.IP_GLOBAL) {
            return clientIp + SEPARATOR + "GLOBAL";
        } else {
            return "DEFAULT" + SEPARATOR + method.name() + SEPARATOR + uri;
        }
    }

    private List<RateLimitConfig> findMatchingConfigs(HttpMethod method, String uri) {
        List<RateLimitConfig> allConfigs = manager.getConfigs();
        List<RateLimitConfig> matched = new ArrayList<>();

        for (RateLimitConfig config : allConfigs) {
            if (!config.isEnabled()) {
                continue;
            }
            boolean methodMatched = config.getMethod() == null
                    || config.getMethod() == method
                    || config.getMethod() == HttpMethod.ALL;
            boolean uriMatched = isUriMatch(config.getPath(), uri);

            if (methodMatched && uriMatched) {
                matched.add(config);
            }
        }
        return matched;
    }

    private boolean isUriMatch(String path, String uri) {
        if (path == null || "*".equals(path)) {
            return true;
        }
        if (path.endsWith("*")) {
            String prefix = path.substring(0, path.length() - 1);
            return uri.startsWith(prefix);
        } else {
            return path.equals(uri);
        }
    }
}
