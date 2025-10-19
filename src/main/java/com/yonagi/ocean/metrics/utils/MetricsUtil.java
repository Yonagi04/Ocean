package com.yonagi.ocean.metrics.utils;

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
 * @date 2025/10/19 16:15
 */
public class MetricsUtil {

    private static final String whiteListString = LocalConfigLoader.getProperty("server.metrics.whitelist", "");

    private static Set<String> whiteList;

    public static Set<String> getWhiteList() {
        if (!whiteListString.isEmpty()) {
            whiteList = Arrays.stream(whiteListString.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            whiteList = Collections.emptySet();
        }
        return whiteList;
    }

    public static String getMetricUri() {
        return LocalConfigLoader.getProperty("server.metrics.uri", "/metrics");
    }
}
