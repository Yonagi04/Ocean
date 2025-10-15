package com.yonagi.ocean.utils;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/15 15:05
 */
public class JsonSerializer {

    public static String serialize(Object object) {
        if (object == null) {
            return "{}";
        }
        // 极简模拟：将对象名和 hashCode 格式化
        return String.format("{\"type\":\"%s\", \"data\":\"%s\"}",
                object.getClass().getSimpleName(),
                object.toString().replaceAll("\"", "\\\"").replaceAll("\n", "\\n"));
    }
}
