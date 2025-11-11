package com.yonagi.ocean.core.loadbalance;

import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/11 13:20
 */
public class HttpClient {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(1000))
            .build();

    public static boolean checkHealth(Upstream upstream) {
        String targetBase = upstream.getUrl();
        String targetPath = LocalConfigLoader.getProperty("server.load_balance.health_check.path", "/health");
        String finalUri = targetBase.replaceAll("/+$", "") + targetPath;
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(new URI(finalUri))
                    .timeout(java.time.Duration.ofMillis(2000))
                    .GET()
                    .build();

            java.net.http.HttpResponse<Void> response = CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            boolean isHealthy = (statusCode >= 200 && statusCode < 300);
            if (!isHealthy) {
                log.error("{}: Health check failed, statusCode: {}", targetBase, statusCode);
            }
            return isHealthy;
        } catch (Exception e) {
            log.error("{}: Health check failed: {}", targetBase, e.getMessage());
            return false;
        }
    }
}
