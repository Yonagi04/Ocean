package com.yonagi.ocean.core.reverseproxy.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.yonagi.ocean.core.loadbalance.config.LoadBalancerConfig;

import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 16:11
 */
@JsonDeserialize(builder = ReverseProxyConfig.Builder.class)
public final class ReverseProxyConfig {

    private final Boolean enabled;

    private final String id;

    private final String path;

    private final Boolean stripPrefix;

    private final Integer timeout;

    private final LoadBalancerConfig lbConfig;

    private final Map<String, String> addHeaders;

    private ReverseProxyConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.id = builder.id;
        this.path = builder.path;
        this.stripPrefix = builder.stripPrefix;
        this.lbConfig = builder.lbConfig;
        this.timeout = builder.timeout;
        this.addHeaders = builder.addHeaders;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public Boolean isStripPrefix() {
        return stripPrefix;
    }

    public LoadBalancerConfig getLbConfig() {
        return lbConfig;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public Map<String, String> getAddHeaders() {
        return addHeaders;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .enabled(this.enabled)
                .id(this.id)
                .path(this.path)
                .stripPrefix(this.stripPrefix)
                .lbConfig(this.lbConfig)
                .timeout(this.timeout)
                .addHeaders(this.addHeaders);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private Boolean enabled;
        private String id;
        private String path;
        private Boolean stripPrefix;
        private LoadBalancerConfig lbConfig;
        private Integer timeout;
        private Map<String, String> addHeaders;

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder stripPrefix(Boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
            return this;
        }

        public Builder lbConfig(LoadBalancerConfig lbConfig) {
            this.lbConfig = lbConfig;
            return this;
        }

        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder addHeaders(Map<String, String> addHeaders) {
            this.addHeaders = addHeaders;
            return this;
        }

        public ReverseProxyConfig build() {
            return new ReverseProxyConfig(this);
        }
    }
}
