package com.yonagi.ocean.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 11:49
 */
public class MimeTypeUtil {
    
    private static final Map<String, String> MIME_MAP = new HashMap<>();
    
    static {
        MIME_MAP.put("html", "text/html");
        MIME_MAP.put("htm", "text/html");
        MIME_MAP.put("css", "text/css");
        MIME_MAP.put("js", "application/javascript");
        MIME_MAP.put("json", "application/json");
        MIME_MAP.put("png", "image/png");
        MIME_MAP.put("jpg", "image/jpeg");
        MIME_MAP.put("jpeg", "image/jpeg");
        MIME_MAP.put("gif", "image/gif");
        MIME_MAP.put("svg", "image/svg+xml");
        MIME_MAP.put("ico", "image/x-icon");
        MIME_MAP.put("txt", "text/plain");
    }
    
    public static String getMimeType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return "application/octet-stream";
        }
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "application/octet-stream");
    }
}
