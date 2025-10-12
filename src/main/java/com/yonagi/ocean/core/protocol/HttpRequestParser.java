package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
//        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1));

        String requestLine = readLineFromInputStream(input);
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        log.info("Request: {}", requestLine);

        HttpRequest.Builder builder = new HttpRequest.Builder();

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            throw new IOException("Invalid request line: " + requestLine);
        }

        String urlPart = parts[1];
        String path = urlPart.split("\\?")[0];
        try {
            builder.method(HttpMethod.valueOf(parts[0]));
            builder.uri(path);
            builder.httpVersion(HttpVersion.getHttpVersion(parts[2]));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid HTTP Syntax in Request Line", e);
        }
        builder.queryParams(parseQueryParams(urlPart));

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLineFromInputStream(input)) != null && !line.isEmpty()) {
            int index = line.indexOf(':');
            if (index > 0) {
                String key = line.substring(0, index).trim().toLowerCase();
                String value = line.substring(index + 1).trim();
                headers.put(key, value);
            }
        }
        builder.headers(headers);

//        if ("POST".equalsIgnoreCase(method) ||
//            "PUT".equalsIgnoreCase(method)) {
//            int contentLength = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
//            char[] bodyChars = new char[contentLength];
//            reader.read(bodyChars);
//            request.setBody(new String(bodyChars).getBytes());
//        }
        builder.rawBodyInputStream(input);

        return builder.build();
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

    private static String readLineFromInputStream(InputStream input) throws IOException {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        int currentByte;
        int prevByte = -1;

        while ((currentByte = input.read()) != -1) {
            lineBuffer.write(currentByte);
            if (prevByte == '\r' && currentByte == '\n') {
                byte[] lineBytes = lineBuffer.toByteArray();
                if (lineBytes.length < 2) {
                    // 仅包含 \r\n
                    return "";
                }
                return new String(lineBytes, 0, lineBytes.length - 2, StandardCharsets.ISO_8859_1);
            }
            prevByte = currentByte;
        }
        if (lineBuffer.size() > 0) {
            return new String(lineBuffer.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        return null;
    }
}
