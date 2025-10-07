package com.yonagi.ocean.core.protocol;/**
* @program Ocean
* @author Yonagi
* @version 1.0
* @description 
* @date 2025/10/07 11:45
*/
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    HEAD,
    OPTIONS,
    PATCH,
    TRACE,
    CONNECT;

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
