package com.yonagi.ocean.core.cors.config;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 14:29
 */
// TODO: CORS策略的细粒度精细到uri级别
public final class CorsConfig {

    private final boolean enabled;

    private final String allowOrigin;

    private final String allowMethods;

    private final String allowHeaders;

    private final String exposeHeaders;

    private final boolean allowCredentials;

    private final int maxAge;

    private CorsConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.allowOrigin = builder.allowOrigin;
        this.allowMethods = builder.allowMethods;
        this.allowHeaders = builder.allowHeaders;
        this.exposeHeaders = builder.exposeHeaders;
        this.allowCredentials = builder.allowCredentials;
        this.maxAge = builder.maxAge;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAllowOrigin() {
        return allowOrigin;
    }

    public String getAllowMethods() {
        return allowMethods;
    }

    public String getAllowHeaders() {
        return allowHeaders;
    }

    public String getExposeHeaders() {
        return exposeHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public int getMaxAge() {
        return maxAge;
    }
    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(this.enabled)
                .allowOrigin(this.allowOrigin)
                .allowMethods(this.allowMethods)
                .allowHeaders(this.allowHeaders)
                .exposeHeaders(this.exposeHeaders)
                .allowCredentials(this.allowCredentials)
                .maxAge(this.maxAge);
    }

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static class Builder {
        private boolean enabled;
        private String allowOrigin;
        private String allowMethods;
        private String allowHeaders;
        private String exposeHeaders;
        private boolean allowCredentials;
        private int maxAge;

        public Builder() {

        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder allowOrigin(String allowOrigin) {
            this.allowOrigin = allowOrigin;
            return this;
        }

        public Builder allowMethods(String allowMethods) {
            this.allowMethods = allowMethods;
            return this;
        }

        public Builder allowHeaders(String allowHeaders) {
            this.allowHeaders = allowHeaders;
            return this;
        }

        public Builder exposeHeaders(String exposeHeaders) {
            this.exposeHeaders = exposeHeaders;
            return this;
        }

        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        public Builder maxAge(int maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public CorsConfig build() {
            return new CorsConfig(this);
        }
    }
}
