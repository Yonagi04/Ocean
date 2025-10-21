package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.configuration.RedirectConfig;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 13:09
 */
public class RedirectHandler implements RequestHandler {

    private final String webRoot;
    private final RedirectConfig redirectConfig;
    private final Set<String> whitelist;
    private final String fallbackUrl;

    private static final Logger log = LoggerFactory.getLogger(RedirectHandler.class);

    public RedirectHandler(String webRoot) {
        this.webRoot = webRoot;
        this.redirectConfig = new RedirectConfig();
        this.whitelist = redirectConfig.getAllowedRedirectHosts();
        this.fallbackUrl = redirectConfig.getFallbackUrl() == null ?
                LocalConfigLoader.getProperty("server.url") + ":" + LocalConfigLoader.getProperty("server.port", "8080") :
                redirectConfig.getFallbackUrl();
    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();
        String targetUrl = (String) request.getAttribute("targetUrl");
        Integer statusCode = (Integer) request.getAttribute("statusCode");
        if (targetUrl == null) {
            log.error("[{}] Missing targetUrl attribute", httpContext.getTraceId());
            return;
        }
        if (statusCode == null) {
            log.warn("[{}] Missing statusCode attribute, using default 302", httpContext.getTraceId());
            statusCode = 302;
        }

        String protocol;
        if ((Boolean) request.getAttribute("isSsl")) {
            protocol = "https";
        } else {
            protocol = "http";
        }
        String host = request.getHeaders().get("Host");
        if (host == null) {
            host = LocalConfigLoader.getProperty("server.url") + ":";
            if (protocol.equalsIgnoreCase("http")) {
                host = host + LocalConfigLoader.getProperty("server.port");
            } else {
                host = host + LocalConfigLoader.getProperty("server.ssl.port", "8443");
            }
        }

        String finalLocation;
        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            try {
                URL url = new URL(targetUrl);
                String originalHostName = url.getHost();
                String hostName = normalizeHost(originalHostName);
                if (!whitelist.isEmpty() && whitelist.contains(hostName)) {
                    finalLocation = targetUrl;
                } else {
                    log.warn("[{}] Host '{}' is not in the whitelist, redirect blocked", httpContext.getTraceId(), hostName);
                    finalLocation = fallbackUrl;
                }
            } catch (MalformedURLException e) {
                log.error("[{}] Malformed URL, redirect to fallback url", httpContext.getTraceId(), e);
                finalLocation = fallbackUrl;
            }
        } else if (targetUrl.startsWith("/")) {
            String baseUrl = protocol + "://" + host;
            finalLocation = baseUrl + targetUrl;
        } else {
            log.warn("[{}] Target URL '{}' is missing protocol. Assuming external redirect with 'https://' for safety", httpContext.getTraceId(), targetUrl);
            String assumedUrl = "https://" + targetUrl;
            try {
                URL url = new URL(assumedUrl);
                String originalHostName = url.getHost();
                String hostName = normalizeHost(originalHostName);
                if (!whitelist.isEmpty() && whitelist.contains(hostName)) {
                    finalLocation = assumedUrl;
                } else {
                    log.warn("[{}] Host '{}' is not in the whitelist, redirect blocked", httpContext.getTraceId(), hostName);
                    finalLocation = fallbackUrl;
                }
            } catch (MalformedURLException e) {
                log.error("[{}] Malformed URL, redirect to fallback url", httpContext.getTraceId(), e);
                finalLocation = fallbackUrl;
            }
        }

        if (statusCode != 301 && statusCode != 302 && statusCode != 303 && statusCode != 307 && statusCode != 308) {
            log.warn("[{}] Invalid statusCode for redirect: {}, using default 302", httpContext.getTraceId(), statusCode);
            statusCode = 302;
        }
        HttpResponse.Builder builder = httpContext.getResponse().toBuilder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.fromCode(statusCode))
                .contentType(request.getAttribute("contentType") != null ? ContentType.fromMime((String) request.getAttribute("contentType")) : ContentType.TEXT_HTML);
        Map<String, String> responseHeaders = (Map<String, String>) request.getAttribute("HstsHeaders");
        responseHeaders.put("Location", finalLocation);
        builder.headers(responseHeaders);

        HttpResponse response = builder.build();
        httpContext.setResponse(response);
        httpContext.setKeepalive(false);
        log.info("[{}] Request path: {}, redirect to: {}, status code: {}", httpContext.getTraceId(), request.getUri(), finalLocation, statusCode);
    }

    private String normalizeHost(String hostName) {
        if (hostName == null) {
            return null;
        }
        String lowerCaseHostName = hostName.toLowerCase();
        if (lowerCaseHostName.startsWith("www.")) {
            return lowerCaseHostName.substring(4);
        }
        return lowerCaseHostName;
    }
}
