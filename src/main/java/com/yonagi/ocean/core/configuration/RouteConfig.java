package com.yonagi.ocean.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.yonagi.ocean.core.protocol.HttpMethod;

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

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "with")
    public static class Builder {
        private boolean enabled;
        private HttpMethod method;
        private String path;
        private String handlerClassName;
        private String contentType;

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

        public RouteConfig build() {
            if (method == null || path == null || handlerClassName == null || contentType == null) {
                throw new IllegalStateException("Method, path, handler class name, and content type must be set");
            }
            return new RouteConfig(this);
        }
    }
}
