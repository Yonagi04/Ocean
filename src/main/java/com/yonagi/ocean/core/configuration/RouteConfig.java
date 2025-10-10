package com.yonagi.ocean.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.router.RouteType;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/08 11:29
 */
@JsonDeserialize(builder = RouteConfig.Builder.class)
public final class RouteConfig {

    private final boolean enabled;

    private final HttpMethod method;

    private final String path;

    private final String handlerClassName;

    private final String contentType;

    private final RouteType routeType;

    private final String targetUrl;

    private final Integer statusCode;

    private RouteConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.method = builder.method;
        this.path = builder.path;
        this.handlerClassName = builder.handlerClassName;
        this.contentType = builder.contentType;
        this.routeType = builder.routeType;
        this.targetUrl = builder.targetUrl;
        this.statusCode = builder.statusCode;
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

    public RouteType getRouteType() {
        return routeType;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .withEnabled(this.enabled)
                .withMethod(this.method)
                .withPath(this.path)
                .withHandlerClassName(this.handlerClassName)
                .withContentType(this.contentType)
                .withRouteType(this.routeType)
                .withTargetUrl(this.targetUrl)
                .withStatusCode(this.statusCode);
    }

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "with")
    public static class Builder {
        private boolean enabled;
        private HttpMethod method;
        private String path;
        private String handlerClassName;
        private String contentType;
        private RouteType routeType;
        private String targetUrl;
        private Integer statusCode;

        public Builder() {

        }

        public Builder withEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder withMethod(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder withPath(String path) {
            this.path = path;
            return this;
        }

        @JsonProperty("handler")
        public Builder withHandlerClassName(String handlerClassName) {
            this.handlerClassName = handlerClassName;
            return this;
        }

        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        @JsonProperty("type")
        public Builder withRouteType(RouteType routeType) {
            this.routeType = routeType;
            return this;
        }

        public Builder withTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }

        public Builder withStatusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public RouteConfig build() {
            if (method == null || path == null || routeType == null || contentType == null) {
                throw new IllegalStateException("Method, path, routeType, and content type must be set");
            }
            if (routeType == RouteType.REDIRECT && (targetUrl == null || statusCode == null)) {
                throw new IllegalStateException("Redirect routes must have targetUrl and statusCode set");
            }
            if (routeType == RouteType.HANDLER && (handlerClassName == null || handlerClassName.isEmpty())) {
                throw new IllegalStateException("Handler routes must have handlerClassName set");
            }
            return new RouteConfig(this);
        }
    }
}
