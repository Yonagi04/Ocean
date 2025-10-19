package com.yonagi.ocean.admin.health;

import com.yonagi.ocean.admin.health.enums.HealthStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 18:40
 */
public class HealthCheckService {

    private final Map<String, HealthIndicator> indicators;

    public HealthCheckService(List<HealthIndicator> indicators) {
        this.indicators = indicators.stream()
                .collect(Collectors.toMap(HealthIndicator::getName, Function.identity()));
    }

    public HealthComponent performHealthCheck() {
        HealthStatus overallStatus = HealthStatus.UP;
        Map<String, Object> allDetails = new HashMap<>();

        for (Map.Entry<String, HealthIndicator> entry : indicators.entrySet()) {
            HealthComponent component = entry.getValue().check();
            allDetails.put(entry.getKey(), component);

            if (component.getStatus() == HealthStatus.DOWN) {
                overallStatus = HealthStatus.DOWN;
            } else if (component.getStatus() == HealthStatus.OUT_OF_SERVICE && overallStatus == HealthStatus.UP) {
                overallStatus = HealthStatus.OUT_OF_SERVICE;
            }
        }
        allDetails.put("status", overallStatus);
        return new HealthComponent(overallStatus, allDetails);
    }
}
