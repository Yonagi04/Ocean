package com.yonagi.ocean.admin.health.impl;

import com.yonagi.ocean.admin.health.HealthComponent;
import com.yonagi.ocean.admin.health.HealthIndicator;
import com.yonagi.ocean.admin.health.enums.HealthStatus;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.NacosConfigLoader;

import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 17:16
 */
public class NacosHealthIndicator implements HealthIndicator {

    @Override
    public HealthComponent check() {
        boolean nacosEnabled = Boolean.parseBoolean(LocalConfigLoader.getProperty("nacos.enabled"));
        if (!nacosEnabled) {
            return new HealthComponent(HealthStatus.OUT_OF_SERVICE, Map.of("connection", "Nacos is disabled"));
        }

        String serverAddr = LocalConfigLoader.getProperty("nacos.server_addr");
        if (NacosConfigLoader.checkNacosConnectivity(serverAddr)) {
            return new HealthComponent(HealthStatus.UP, Map.of("connection", "Nacos is up"));
        } else {
            return new HealthComponent(HealthStatus.DOWN, Map.of("connection", "Failed to ping Nacos at " + serverAddr));
        }
    }

    @Override
    public String getName() {
        return "nacos";
    }
}
