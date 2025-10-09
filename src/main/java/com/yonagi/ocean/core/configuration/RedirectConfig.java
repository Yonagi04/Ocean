package com.yonagi.ocean.core.configuration;

import com.yonagi.ocean.utils.LocalConfigLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 16:01
 */
public final class RedirectConfig {

    private final Set<String> ALLOWED_REDIRECT_HOSTS;

    private final String FALLBACK_URL;

    public RedirectConfig() {
        String whiteListString = LocalConfigLoader.getProperty("server.redirect.whitelist");
        if (whiteListString != null && !whiteListString.isEmpty()) {
            this.ALLOWED_REDIRECT_HOSTS = Arrays.stream(whiteListString.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            this.ALLOWED_REDIRECT_HOSTS = Collections.emptySet();
        }
        String fallback = LocalConfigLoader.getProperty("server.redirect.fallback_url");
        this.FALLBACK_URL = fallback != null ? fallback.trim() : null;
    }

    public Set<String> getAllowedRedirectHosts() {
        return ALLOWED_REDIRECT_HOSTS;
    }

    public String getFallbackUrl() {
        return FALLBACK_URL;
    }
}
