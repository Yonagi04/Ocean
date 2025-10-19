package com.yonagi.ocean.admin.health;

import com.yonagi.ocean.admin.health.enums.HealthStatus;

import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 17:11
 */
public class HealthComponent {

    private final HealthStatus status;

    private final Map<String, Object> details;

    public HealthComponent(HealthStatus status, Map<String, Object> details) {
        this.status = status;
        this.details = details;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
