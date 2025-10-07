package com.yonagi.ocean.core.protocol;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 12:54
 */
public enum HttpVersion {
    HTTP_1_0("HTTP/1.0"),
    HTTP_1_1("HTTP/1.1"),
    HTTP_2_0("HTTP/2.0"),
    HTTP_3_0("HTTP/3.0");

    private final String version;

    HttpVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public static HttpVersion getHttpVersion(String version) {
        for (HttpVersion httpVersion : values()) {
            if (httpVersion.getVersion().equalsIgnoreCase(version)) {
                return httpVersion;
            }
        }
        return null;
    }
}
