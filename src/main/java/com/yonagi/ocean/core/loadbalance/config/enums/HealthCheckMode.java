package com.yonagi.ocean.core.loadbalance.config.enums;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/10 19:04
 */
public enum HealthCheckMode {
    ACTIVE_CHECK,
    PASSIVE_CHECK,
    DISABLED;

    public static HealthCheckMode getHealthCheckStatus(String status) {
        try {
            return HealthCheckMode.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.name();
    }
}
