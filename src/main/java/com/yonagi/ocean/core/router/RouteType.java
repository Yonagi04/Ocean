package com.yonagi.ocean.core.router;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/09 11:47
 */
public enum RouteType {
    STATIC,
    HANDLER,
    REDIRECT;

    public static RouteType getRouteType(String type) {
        try {
            return RouteType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    @Override
    public String toString() {
        return this.name();
    }
}
