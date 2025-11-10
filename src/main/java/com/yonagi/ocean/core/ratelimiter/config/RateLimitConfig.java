package com.yonagi.ocean.core.ratelimiter.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.yonagi.ocean.core.ratelimiter.config.enums.RateLimitType;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 11:38
 */
@JsonDeserialize(builder = RateLimitConfig.Builder.class)
public final class RateLimitConfig {

    private final boolean enabled;

    private final HttpMethod method;

    private final String path;

    private final List<RateLimitType> scopes;

    private RateLimitConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.method = builder.method;
        this.path = builder.path;
        this.scopes = builder.scopes;
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

    public List<RateLimitType> getScopes() {
        return scopes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(this.enabled)
                .method(this.method)
                .path(this.path)
                .scopes(this.scopes);
    }

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static class Builder {
        private boolean enabled;
        private HttpMethod method;
        private String path;
        private List<RateLimitType> scopes;

        public Builder() {

        }

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

        public Builder scopes(List<RateLimitType> scopes) {
            this.scopes = scopes;
            return this;
        }

        public RateLimitConfig build() {
            return new RateLimitConfig(this);
        }
    }
}