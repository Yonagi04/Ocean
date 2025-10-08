package com.yonagi.ocean.config;

import com.yonagi.ocean.core.protocol.HttpMethod;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 11:29
 */
public final class RouteConfig {

    private final boolean enabled;

    private final HttpMethod method;

    private final String path;

    private final String handlerClassName;

    private final String contentType;

    private RouteConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.method = builder.method;
        this.path = builder.path;
        this.handlerClassName = builder.handlerClassName;
        this.contentType = builder.contentType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getHandlerClassName() {
        return handlerClassName;
    }

    public String getContentType() {
        return contentType;
    }

    public static class Builder {
        private boolean enabled;
        private HttpMethod method;
        private String path;
        private String handlerClassName;
        private String contentType;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder handlerClassName(String handlerClassName) {
            this.handlerClassName = handlerClassName;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public RouteConfig build() {
            if (method == null || path == null || handlerClassName == null || contentType == null) {
                throw new IllegalStateException("Method, path, handler class name, and content type must be set");
            }
            return new RouteConfig(this);
        }
    }
}
