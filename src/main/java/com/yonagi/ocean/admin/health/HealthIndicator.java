package com.yonagi.ocean.admin.health;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 17:14
 */
public interface HealthIndicator {

    HealthComponent check();

    String getName();
}
