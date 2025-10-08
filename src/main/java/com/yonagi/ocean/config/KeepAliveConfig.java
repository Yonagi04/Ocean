package com.yonagi.ocean.config;

/**
 * HTTP Keep-Alive configuration class
 * 
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description Configuration for HTTP Keep-Alive mechanism
 * @date 2025/01/15
 */
public class KeepAliveConfig {
    
    private final boolean enabled;
    private final int timeoutSeconds;
    private final int maxRequests;
    private final int timeoutCheckIntervalSeconds;
    
    private KeepAliveConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxRequests = builder.maxRequests;
        this.timeoutCheckIntervalSeconds = builder.timeoutCheckIntervalSeconds;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public int getMaxRequests() {
        return maxRequests;
    }
    
    public int getTimeoutCheckIntervalSeconds() {
        return timeoutCheckIntervalSeconds;
    }
    
    public long getTimeoutMillis() {
        return timeoutSeconds * 1000L;
    }
    
    public long getTimeoutCheckIntervalMillis() {
        return timeoutCheckIntervalSeconds * 1000L;
    }
    
    public static class Builder {
        private boolean enabled = true;
        private int timeoutSeconds = 60;
        private int maxRequests = 100;
        private int timeoutCheckIntervalSeconds = 30;
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }
        
        public Builder maxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
            return this;
        }
        
        public Builder timeoutCheckIntervalSeconds(int timeoutCheckIntervalSeconds) {
            this.timeoutCheckIntervalSeconds = timeoutCheckIntervalSeconds;
            return this;
        }
        
        public KeepAliveConfig build() {
            return new KeepAliveConfig(this);
        }
    }
}
