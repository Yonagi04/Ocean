package com.yonagi.ocean.core.reverseproxy.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.yonagi.ocean.core.loadbalance.config.enums.LoadBalancing;

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

    private final String targetUrl;

    private final Boolean stripPrefix;

    private final Integer timeout;

    private final LoadBalancing loadBalancing;

    private final Map<String, String> addHeaders;

    private ReverseProxyConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.id = builder.id;
        this.path = builder.path;
        this.targetUrl = builder.targetUrl;
        this.stripPrefix = builder.stripPrefix;
        this.timeout = builder.timeout;
        this.loadBalancing = builder.loadBalancing;
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

    public String getTargetUrl() {
        return targetUrl;
    }

    public Boolean isStripPrefix() {
        return stripPrefix;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public LoadBalancing getLoadBalancing() {
        return loadBalancing;
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
                .targetUrl(this.targetUrl)
                .stripPrefix(this.stripPrefix)
                .timeout(this.timeout)
                .loadBalancing(this.loadBalancing)
                .addHeaders(this.addHeaders);
    }

    @JsonPOJOBuilder(buildMethodName = "build", withPrefix = "")
    public static class Builder {
        private Boolean enabled;
        private String id;
        private String path;
        private String targetUrl;
        private Boolean stripPrefix;
        private Integer timeout;
        private LoadBalancing loadBalancing;
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

        public Builder targetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
            return this;
        }

        public Builder stripPrefix(Boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
            return this;
        }

        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder loadBalancing(LoadBalancing loadBalancing) {
            this.loadBalancing = loadBalancing;
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
