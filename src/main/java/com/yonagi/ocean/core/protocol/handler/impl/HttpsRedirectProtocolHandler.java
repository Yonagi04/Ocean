package com.yonagi.ocean.core.protocol.handler.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 15:07
 */
public class HttpsRedirectProtocolHandler implements HttpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpsRedirectProtocolHandler.class);

    private final int sslPort;

    public HttpsRedirectProtocolHandler(int sslPort) {
        this.sslPort = sslPort;
    }

    @Override
    public HttpRequest handle(HttpRequest request, OutputStream output) throws IOException {
        String host = request.getHeaders().get("Host");
        if (host == null) {
            host = LocalConfigLoader.getProperty("server.url");
        } else {
            host = host.split(":")[0];
        }
        String originalUri = request.getUri();
        String redirectLocation = "https://" + host + ":" + sslPort + originalUri;
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Location", redirectLocation);
        HttpResponse redirectResponse = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.PERMANENT_REDIRECT)
                .contentType(ContentType.TEXT_HTML)
                .headers(responseHeaders)
                .body(String.format("Redirecting to %s", redirectLocation).getBytes())
                .build();
        redirectResponse.write(request, output, false);
        log.info("Redirected HTTP request to HTTPS: {}", redirectLocation);
        return null;
    }
}
