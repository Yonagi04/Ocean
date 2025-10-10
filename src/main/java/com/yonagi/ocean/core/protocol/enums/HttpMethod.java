package com.yonagi.ocean.core.protocol.enums;/**
* @program Ocean
* @author Yonagi
* @version 1.0
* @description 
* @date 2025/10/07 11:45
*/
public enum HttpMethod {
    // ALL means all methods, only used for route matching and rate limiting
    GET,
    POST,
    PUT,
    DELETE,
    HEAD,
    OPTIONS,
    PATCH,
    TRACE,
    CONNECT,
    ALL;

    public static HttpMethod get(String method) {
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return this.name();
    }
}
