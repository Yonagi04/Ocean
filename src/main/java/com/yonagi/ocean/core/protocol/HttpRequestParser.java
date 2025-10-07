package com.yonagi.ocean.core.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 10:06
 */
public class HttpRequestParser {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestParser.class);

    public static HttpRequest parse(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        log.info("Request: {}", requestLine);

        HttpRequest request = new HttpRequest.Builder()
                .build();

        String[] parts = requestLine.split(" ");
        String method = parts[0];
        String urlPart = parts[1];
        String httpVersion = parts[2];
        String path = urlPart.split("\\?")[0];
        Map<String, String> queryParams = parseQueryParams(urlPart);

        request.setMethod(HttpMethod.get(method));
        request.setUri(path);
        request.setHttpVersion(HttpVersion.getHttpVersion(httpVersion));
        request.setQueryParams(queryParams);

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int index = line.indexOf(':');
            if (index > 0) {
                String key = line.substring(0, index).trim().toLowerCase();
                String value = line.substring(index + 1).trim();
                headers.put(key, value);
            }
        }
        request.setHeaders(headers);

        if ("POST".equalsIgnoreCase(method) ||
            "PUT".equalsIgnoreCase(method)) {
            int contentLength = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
            char[] bodyChars = new char[contentLength];
            reader.read(bodyChars);
            request.setBody(new String(bodyChars).getBytes());
        }

        return request;
    }

    private static Map<String, String> parseQueryParams(String urlPart) {
        Map<String, String> queryParams = new HashMap<>();
        int index = urlPart.indexOf('?');
        if (index < 0) {
            return queryParams;
        }
        String query = urlPart.substring(index + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                queryParams.put(kv[0], kv[1]);
            } else if (kv.length == 1) {
                queryParams.put(kv[0], "");
            }
        }
        return queryParams;
    }
}
