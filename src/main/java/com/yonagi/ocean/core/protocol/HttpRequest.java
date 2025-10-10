package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;

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

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setHttpVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
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

    public static class Builder {
        private HttpMethod method;
        private String uri;
        private HttpVersion httpVersion;
        private Map<String, String> headers;
        private byte[] body;
        private Map<String, String> queryParams;

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

        public HttpRequest build() {
            HttpRequest request = new HttpRequest();
            request.method = this.method;
            request.uri = this.uri;
            request.httpVersion = this.httpVersion;
            request.headers = this.headers;
            request.body = this.body;
            request.queryParams = this.queryParams;
            return request;
        }
    }
}
