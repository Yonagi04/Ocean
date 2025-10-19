package com.yonagi.ocean.admin.health.enums;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 17:11
 */
public enum HealthStatus {
    UP(200),
    DOWN(503),
    OUT_OF_SERVICE(503);

    private final int httpStatusCode;

    HealthStatus(int httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }
}
