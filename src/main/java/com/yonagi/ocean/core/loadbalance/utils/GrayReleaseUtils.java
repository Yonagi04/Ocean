package com.yonagi.ocean.core.loadbalance.utils;

import com.yonagi.ocean.core.protocol.HttpRequest;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/13 14:11
 */
public class GrayReleaseUtils {

    public static boolean isGrayRelease(HttpRequest request, Integer canaryPercent) {
        String sessionId = request.getAttribute().getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        return (sessionId.hashCode() & 0xFFFF) % 100 < canaryPercent;
    }
}
