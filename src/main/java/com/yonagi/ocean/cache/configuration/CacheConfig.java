package com.yonagi.ocean.cache.configuration;

/**
 * 缓存配置的不可变值对象
 */
public final class CacheConfig {

    public enum Type { LRU, CAFFEINE, NONE }

    private final boolean enabled;
    private final Type type;
    private final long cleanerIntervalMs;

    // LRU 专属
    private final int lruMaxEntries;
    private final long lruTtlMs;
    private final String lruPolicy;
    private final long lruMaxMemoryMb;
    private final boolean lruDynamicAdjustment;
    private final long lruAdjustIntervalMs;

    // Caffeine 专属
    private final String caffeineExpireType;
    private final long caffeineTtlMs;
    private final String caffeinePolicy;
    private final long caffeineMaxMemoryMb;
    private final long caffeineMaxEntries;
    private final boolean caffeineSoftValues;

    private CacheConfig(Builder b) {
        this.enabled = b.enabled;
        this.type = b.type;
        this.cleanerIntervalMs = b.cleanerIntervalMs;
        this.lruMaxEntries = b.lruMaxEntries;
        this.lruTtlMs = b.lruTtlMs;
        this.lruPolicy = b.lruPolicy;
        this.lruMaxMemoryMb = b.lruMaxMemoryMb;
        this.lruDynamicAdjustment = b.lruDynamicAdjustment;
        this.lruAdjustIntervalMs = b.lruAdjustIntervalMs;
        this.caffeineExpireType = b.caffeineExpireType;
        this.caffeineTtlMs = b.caffeineTtlMs;
        this.caffeinePolicy = b.caffeinePolicy;
        this.caffeineMaxMemoryMb = b.caffeineMaxMemoryMb;
        this.caffeineMaxEntries = b.caffeineMaxEntries;
        this.caffeineSoftValues = b.caffeineSoftValues;
    }

    public boolean isEnabled() { return enabled; }
    public Type getType() { return type; }
    public long getCleanerIntervalMs() { return cleanerIntervalMs; }
    public int getLruMaxEntries() { return lruMaxEntries; }
    public long getLruTtlMs() { return lruTtlMs; }
    public String getLruPolicy() { return lruPolicy; }
    public long getLruMaxMemoryMb() { return lruMaxMemoryMb; }
    public boolean isLruDynamicAdjustment() { return lruDynamicAdjustment; }
    public long getLruAdjustIntervalMs() { return lruAdjustIntervalMs; }
    public String getCaffeineExpireType() { return caffeineExpireType; }
    public long getCaffeineTtlMs() { return caffeineTtlMs; }
    public String getCaffeinePolicy() { return caffeinePolicy; }
    public long getCaffeineMaxMemoryMb() { return caffeineMaxMemoryMb; }
    public long getCaffeineMaxEntries() { return caffeineMaxEntries; }
    public boolean isCaffeineSoftValues() { return caffeineSoftValues; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean enabled;
        private Type type = Type.NONE;
        private long cleanerIntervalMs = 60000L;
        private int lruMaxEntries = 100;
        private long lruTtlMs = 60000L;
        private String lruPolicy = "SIZE";
        private long lruMaxMemoryMb = 64L;
        private boolean lruDynamicAdjustment = false;
        private long lruAdjustIntervalMs = 60000L;
        private String caffeineExpireType = "WRITE";
        private long caffeineTtlMs = 60000L;
        private String caffeinePolicy = "SIZE";
        private long caffeineMaxMemoryMb = 100L;
        private long caffeineMaxEntries = 100L;
        private boolean caffeineSoftValues = false;

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder type(Type type) { this.type = type; return this; }
        public Builder cleanerIntervalMs(long v) { this.cleanerIntervalMs = v; return this; }
        public Builder lruMaxEntries(int v) { this.lruMaxEntries = v; return this; }
        public Builder lruTtlMs(long v) { this.lruTtlMs = v; return this; }
        public Builder lruPolicy(String v) { this.lruPolicy = v; return this; }
        public Builder lruMaxMemoryMb(long v) { this.lruMaxMemoryMb = v; return this; }
        public Builder lruDynamicAdjustment(boolean v) { this.lruDynamicAdjustment = v; return this; }
        public Builder lruAdjustIntervalMs(long v) { this.lruAdjustIntervalMs = v; return this; }
        public Builder caffeineExpireType(String v) { this.caffeineExpireType = v; return this; }
        public Builder caffeineTtlMs(long v) { this.caffeineTtlMs = v; return this; }
        public Builder caffeinePolicy(String v) { this.caffeinePolicy = v; return this; }
        public Builder caffeineMaxMemoryMb(long v) { this.caffeineMaxMemoryMb = v; return this; }
        public Builder caffeineMaxEntries(long v) { this.caffeineMaxEntries = v; return this; }
        public Builder caffeineSoftValues(boolean v) { this.caffeineSoftValues = v; return this; }

        public CacheConfig build() { return new CacheConfig(this); }
    }
}


