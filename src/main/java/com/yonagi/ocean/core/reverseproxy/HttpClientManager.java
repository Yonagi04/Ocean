package com.yonagi.ocean.core.reverseproxy;

import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/12 13:52
 */
public class HttpClientManager {

    private static final Logger log = LoggerFactory.getLogger(HttpClientManager.class);

    private static final long CONNECT_TIMEOUT_MS = Long.parseLong(LocalConfigLoader.getProperty("server.reverse_proxy.connect_timeout_millis", "5000"));

    private static final Map<String, java.net.http.HttpClient> clients = new ConcurrentHashMap<>();

    public static HttpClient getClient(URI uri) {
        String upstreamUrl = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        return clients.computeIfAbsent(upstreamUrl, key -> HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(Executors.newFixedThreadPool(32))
                .build());
    }

    public static void removeClient(String upstreamUrl) {
        clients.remove(upstreamUrl);
        log.debug("Removed Http Client for upstream url: {}", upstreamUrl);
    }
}
