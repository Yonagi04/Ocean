package com.yonagi.ocean.core.loadbalance.config.enums;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:12
 */
public enum Strategy {
    NONE,
    ROUND_ROBIN,
    IP_HASH,
    RANDOM;

    public static Strategy getLbStrategy(String type) {
        try {
            return Strategy.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.name();
    }
}
