package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 16:24
 */
public class HttpResponse {
    private HttpVersion httpVersion;
    private HttpStatus httpStatus;
    private String contentType;
    private byte[] body;

    // Additional headers, e.g., for CORS, caching, etc.
    private Map<String, String> headers;

    private HttpResponse() {

    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Builder toBuilder() {
        Map<String, String> headerCopy = headers != null ? new HashMap<>(headers) : null;
        return new Builder()
                .httpVersion(httpVersion)
                .httpStatus(httpStatus)
                .contentType(contentType)
                .headers(headerCopy);
    }

    /**
     * 将响应以正确的 HTTP 报文格式写入输出流，支持Keep-Alive
     */
    public void write(HttpRequest request, OutputStream outputStream, boolean keepAlive) throws IOException {
        Map<String, String> corsHeaders = (Map<String, String>) request.getAttribute("CorsResponseHeaders");
        if (corsHeaders != null && !corsHeaders.isEmpty()) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.putAll(corsHeaders);
        }

        boolean forbidsBody = httpStatus.getCode() == 204
                || httpStatus.getCode() == 304
                || httpStatus.getCode() / 100 == 1;

        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append(httpVersion.getVersion()).append(" ")
                .append(httpStatus.toString()).append("\r\n");

        String contentLengthValue = null;
        String contentTypeValue = this.contentType;

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                if (key.equalsIgnoreCase("Connection")) {
                    continue;
                }
                if (key.equalsIgnoreCase("Content-Type")) {
                    contentTypeValue = entry.getValue();
                    continue;
                }
                if (key.equalsIgnoreCase("Content-Length")) {
                    contentLengthValue = entry.getValue();
                    continue;
                }
                headerBuilder.append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\r\n");
            }
        }

        if (!forbidsBody && contentTypeValue != null) {
            headerBuilder.append("Content-Type: ").append(contentTypeValue).append("\r\n");
        }

        if (!forbidsBody) {
            if (body != null) {
                headerBuilder.append("Content-Length: ").append(body.length).append("\r\n");
            } else if (contentLengthValue != null) {
                headerBuilder.append("Content-Length: ").append(contentLengthValue).append("\r\n");
            } else {
                headerBuilder.append("Content-Length: 0\r\n");
            }
        }

        if (keepAlive) {
            headerBuilder.append("Connection: keep-alive\r\n");
        } else {
            headerBuilder.append("Connection: close\r\n");
        }

        headerBuilder.append("\r\n");

        // 写入头
        outputStream.write(headerBuilder.toString().getBytes(StandardCharsets.ISO_8859_1));
        // 写入体（若有）
        if (body != null && body.length > 0) {
            outputStream.write(body);
        }
    }

    public void writeStreaming(HttpRequest request, OutputStream outputStream, boolean keepAlive, long contentLength) throws IOException {
        Map<String, String> corsHeaders = (Map<String, String>) request.getAttribute("CorsResponseHeaders");
        if (corsHeaders != null && !corsHeaders.isEmpty()) {
            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            this.headers.putAll(corsHeaders);
        }

        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append(httpVersion.getVersion()).append(" ")
                .append(httpStatus.toString()).append("\r\n");
        headerBuilder.append("Content-Type: ").append(contentType).append("\r\n");

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                if (key.equalsIgnoreCase("Content-Type") ||
                        key.equalsIgnoreCase("Connection") ||
                        key.equalsIgnoreCase("Content-Length")) {
                    continue;
                }
                headerBuilder.append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\r\n");
            }
        }
        headerBuilder.append("Content-Length: ").append(contentLength).append("\r\n");

        if (keepAlive) {
            headerBuilder.append("Connection: keep-alive\r\n");
        } else {
            headerBuilder.append("Connection: close\r\n");
        }
        headerBuilder.append("\r\n");
        outputStream.write(headerBuilder.toString().getBytes(StandardCharsets.ISO_8859_1));
        // 不写入body，长文件流交给处理器来写入
    }

    @Override
    public String toString() {
        return httpVersion.getVersion() + " " + httpStatus.toString() + "\r\n" +
               "Content-Type: " + contentType + "\r\n" +
                (headers != null ? headers.entrySet().stream()
                        .filter(entry -> {
                            String key = entry.getKey();
                            return key != null &&
                                   !key.equalsIgnoreCase("Content-Type") &&
                                   !key.equalsIgnoreCase("Content-Length");
                        })
                        .map(entry -> entry.getKey() + ": " + entry.getValue() + "\r\n")
                        .reduce("", String::concat) : "") +
               "Content-Length: " + (body != null ? body.length : 0) + "\r\n" +
               "\r\n" +
               (body != null ? new String(body) : "");
    }

    public static class Builder {
        private HttpVersion httpVersion;
        private HttpStatus httpStatus;
        private String contentType;
        private byte[] body;
        private Map<String, String> headers;

        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder httpStatus(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers != null ? new HashMap<>(headers) : null;
            return this;
        }

        public HttpResponse build() {
            if (httpStatus == null || contentType == null) {
                throw new IllegalStateException("Status code, status text, and content type must be set");
            }
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.httpVersion = this.httpVersion != null ? this.httpVersion : HttpVersion.HTTP_1_1;
            httpResponse.httpStatus = this.httpStatus;
            httpResponse.contentType = this.contentType;
            httpResponse.body = this.body;
            httpResponse.headers = this.headers;
            return httpResponse;
        }
    }
}
