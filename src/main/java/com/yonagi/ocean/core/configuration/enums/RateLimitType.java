package com.yonagi.ocean.core.configuration.enums;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 11:40
 */
public enum RateLimitType {
    IP_URI,
    IP_GLOBAL,
    GLOBAL_URI;

    public static RateLimitType getRateLimitType(String type) {
        try {
            return RateLimitType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.name();
    }
}
