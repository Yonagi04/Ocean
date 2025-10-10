package com.yonagi.ocean.utils;

import com.yonagi.ocean.core.protocol.HttpRequest;

import java.util.Arrays;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 15:15
 */
public class IpExtractor {

    private static final List<String> IP_HEADER_CANDIDATES = Arrays.asList(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    public String getClientIp(HttpRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADER_CANDIDATES) {
            String ipChain = request.getHeaders().get(header);
            if (ipChain != null && !ipChain.isEmpty() && !"unknown".equalsIgnoreCase(ipChain)) {
                String clientIp = ipChain.split(",")[0].trim();
                if (isValidIp(clientIp)) {
                    return clientIp;
                }
            }
        }
        String clientIp = (String) request.getAttribute("clientIp");
        if (clientIp != null && !clientIp.isEmpty()) {
            return clientIp;
        }
        return "UNKNOWN_IP";
    }

    private boolean isValidIp(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        return clientIp.matches("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b") ||
               clientIp.matches("([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}") ||
               clientIp.matches("((?:[0-9a-fA-F]{1,4}:){1,7}:)|") ||
               clientIp.matches("((?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4})|") ||
               clientIp.matches("((?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2})|") ||
               clientIp.matches("((?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3})|") ||
               clientIp.matches("((?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4})|") ||
               clientIp.matches("((?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5})|") ||
               clientIp.matches("([0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6})|") ||
               clientIp.matches("(:(?::[0-9a-fA-F]{1,4}){1,7}|:)|") ||
               clientIp.matches("(fe80:(?::[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,})|");
    }
}
