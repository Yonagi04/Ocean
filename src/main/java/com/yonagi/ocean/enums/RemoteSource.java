package com.yonagi.ocean.enums;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/15 14:54
 */
public enum RemoteSource {
    NACOS("nacos"),
    APOLLO("apollo");

    private final String name;

    RemoteSource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static List<RemoteSource> getAllRemoteSources() {
        return List.of(RemoteSource.values());
    }
}
