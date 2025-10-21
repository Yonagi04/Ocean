package com.yonagi.ocean.admin.utils;

import com.yonagi.ocean.utils.LocalConfigLoader;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
public class AdminUtil {

    private static final String whiteListString = LocalConfigLoader.getProperty("server.admin.whitelist", "");

    private static Set<String> whiteList;

    private static Set<String> getWhiteList() {
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
        return LocalConfigLoader.getProperty("server.admin.metrics.uri", "/metrics");
    }

    public static String getHealthUri() {
        return LocalConfigLoader.getProperty("server.admin.health.uri", "/health");
    }

    public static String getAdminUri() {
        return LocalConfigLoader.getProperty("server.admin.uri", "/admin");
    }

    private static boolean isInRange(String ipAddress, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }

            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            long ipLong = ipToLong(ipAddress);
            long networkLong = ipToLong(networkAddress);

            long mask = 0xFFFFFFFFL << (32 - prefixLength);

            return (ipLong & mask) == (networkLong & mask);

        } catch (Exception e) {
            return false;
        }
    }

    private static long ipToLong(String ipAddress) throws UnknownHostException {
        InetAddress ip = InetAddress.getByName(ipAddress);
        byte[] octets = ip.getAddress();
        long result = 0;
        for (byte octet : octets) {
            result <<= 8;
            result |= octet & 0xFF;
        }
        return result;
    }

    public static boolean isIpInWhiteList(String clientIp) {

        Set<String> whiteList = getWhiteList();
        if (whiteList.isEmpty()) {
            return false;
        }
        for (String entry : whiteList) {
            if (entry.contains("/")) {
                if (isInRange(clientIp, entry)) {
                    return true;
                }
            } else {
                if (clientIp.equalsIgnoreCase(entry)) {
                    return true;
                }
            }
        }
        return false;
    }
}
