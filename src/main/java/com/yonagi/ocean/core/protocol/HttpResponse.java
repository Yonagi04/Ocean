package com.yonagi.ocean.core.protocol;

import java.io.IOException;
import java.io.OutputStream;
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
    private Integer statusCode;
    private String statusText;
    private String contentType;
    private byte[] body;

    // Additional headers, e.g., for CORS, caching, etc.
    private Map<String, String> headers;

    private HttpResponse() {

    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
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

    /**
     * 将响应以正确的 HTTP 报文格式写入输出流，避免将二进制 body 进行字符串编码导致内容损坏。
     */
    public void write(OutputStream outputStream) throws IOException {
        write(outputStream, true); // Default to keep-alive
    }
    
    /**
     * 将响应以正确的 HTTP 报文格式写入输出流，支持Keep-Alive
     */
    public void write(OutputStream outputStream, boolean keepAlive) throws IOException {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append(httpVersion.getVersion()).append(" ")
                .append(statusCode).append(" ").append(statusText).append("\r\n");
        headerBuilder.append("Content-Type: ").append(contentType).append("\r\n");

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    continue;
                }
                if (key.equalsIgnoreCase("Content-Type") || 
                    key.equalsIgnoreCase("Content-Length") || 
                    key.equalsIgnoreCase("Connection")) {
                    continue;
                }
                headerBuilder.append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\r\n");
            }
        }

        int contentLength = body != null ? body.length : 0;
        headerBuilder.append("Content-Length: ").append(contentLength).append("\r\n");
        
        // Add Connection header based on keepAlive parameter
        if (keepAlive) {
            headerBuilder.append("Connection: keep-alive\r\n");
        } else {
            headerBuilder.append("Connection: close\r\n");
        }
        
        headerBuilder.append("\r\n");

        // 写入头
        outputStream.write(headerBuilder.toString().getBytes());
        // 写入体（若有）
        if (body != null && body.length > 0) {
            outputStream.write(body);
        }
    }

    @Override
    public String toString() {
        return httpVersion.getVersion() + " " + statusCode + " " + statusText + "\r\n" +
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
        private Integer statusCode;
        private String statusText;
        private String contentType;
        private byte[] body;
        private Map<String, String> headers;

        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder statusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder statusText(String statusText) {
            this.statusText = statusText;
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
            this.headers = headers;
            return this;
        }

        public HttpResponse build() {
            if (statusCode == null || statusText == null || contentType == null) {
                throw new IllegalStateException("Status code, status text, and content type must be set");
            }
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.httpVersion = this.httpVersion != null ? this.httpVersion : HttpVersion.HTTP_1_1;
            httpResponse.statusCode = this.statusCode;
            httpResponse.statusText = this.statusText;
            httpResponse.contentType = this.contentType;
            httpResponse.body = this.body;
            httpResponse.headers = this.headers;
            return httpResponse;
        }
    }
}
