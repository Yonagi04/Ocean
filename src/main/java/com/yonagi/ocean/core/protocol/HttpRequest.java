package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import org.apache.commons.io.input.ReaderInputStream;

import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 09:58
 */
public class HttpRequest {
    private HttpMethod method;
    private String uri;
    private HttpVersion httpVersion;
    private Map<String, String> headers;
    private byte[] body;
    private Map<String, String> queryParams;

    // FileUploadHandler 或 ApiHandler 调用这个流获取body数据
    private InputStream rawBodyInputStream;

    // Ocean内部使用的属性存储
    private final Map<String, Object> attributes = new HashMap<>();

    private HttpRequest() {

    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public InputStream getRawBodyInputStream() {
        return rawBodyInputStream;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    @Override
    public String toString() {
        return method.toString() + " " + uri + " " + httpVersion + "\r\n" +
               headers.entrySet().stream()
                       .map(entry -> entry.getKey() + ": " + entry.getValue())
                       .reduce("", (a, b) -> a + b + "\r\n") +
               "\r\n" +
               (body != null ? new String(body) : "");
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public Object getAttributeOrDefault(String key, Object defaultValue) {
        if (attributes.containsKey(key)) {
            return attributes.get(key);
        }
        return defaultValue;
    }

    public Builder toBuilder() {
        return new Builder()
                .method(this.method)
                .uri(this.uri)
                .httpVersion(this.httpVersion)
                .headers(this.headers)
                .body(this.body)
                .queryParams(this.queryParams)
                .rawBodyInputStream(this.rawBodyInputStream);
    }

    public static class Builder {
        private HttpMethod method;
        private String uri;
        private HttpVersion httpVersion;
        private Map<String, String> headers;
        private byte[] body;
        private Map<String, String> queryParams;

        private InputStream rawBodyInputStream;

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Builder queryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder rawBodyInputStream(InputStream rawBodyInputStream) {
            this.rawBodyInputStream = rawBodyInputStream;
            return this;
        }

        public HttpRequest build() {
            HttpRequest request = new HttpRequest();
            request.method = this.method;
            request.uri = this.uri;
            request.httpVersion = this.httpVersion;
            request.headers = this.headers;
            request.body = this.body;
            request.queryParams = this.queryParams;
            request.rawBodyInputStream = this.rawBodyInputStream;
            return request;
        }
    }
}
