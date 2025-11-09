package com.yonagi.ocean.core.configuration.enums;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:12
 */
public enum LoadBalancing {
    NONE,
    ROUND_ROBIN,
    RANDOM;

    public static LoadBalancing getLbType(String type) {
        try {
            return LoadBalancing.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.name();
    }
}
