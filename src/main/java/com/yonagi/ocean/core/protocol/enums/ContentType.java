package com.yonagi.ocean.core.protocol.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 11:42
 */
public enum ContentType {

    TEXT_PLAIN("text/plain; charset=utf-8"),
    TEXT_HTML("text/html; charset=utf-8"),
    TEXT_CSS("text/css; charset=utf-8"),
    TEXT_JAVASCRIPT("text/javascript; charset=utf-8"),

    APPLICATION_JSON("application/json; charset=utf-8"),
    APPLICATION_XML("application/xml; charset=utf-8"),
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded; charset=utf-8"),
    MULTIPART_FORM_DATA("multipart/form-data"),

    IMAGE_PNG("image/png"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_GIF("image/gif"),
    IMAGE_WEBP("image/webp"),
    IMAGE_SVG("image/svg+xml"),

    UNKNOWN("application/octet-stream");

    private final String value;

    ContentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    private static final Map<String, ContentType> EXTENSION_MAP = new HashMap<>();
    private static final Map<String, ContentType> MIME_MAP = new HashMap<>();

    static {
        EXTENSION_MAP.put("txt", TEXT_PLAIN);
        EXTENSION_MAP.put("html", TEXT_HTML);
        EXTENSION_MAP.put("htm", TEXT_HTML);
        EXTENSION_MAP.put("css", TEXT_CSS);
        EXTENSION_MAP.put("js", TEXT_JAVASCRIPT);
        EXTENSION_MAP.put("json", APPLICATION_JSON);
        EXTENSION_MAP.put("xml", APPLICATION_XML);
        EXTENSION_MAP.put("png", IMAGE_PNG);
        EXTENSION_MAP.put("jpg", IMAGE_JPEG);
        EXTENSION_MAP.put("jpeg", IMAGE_JPEG);
        EXTENSION_MAP.put("gif", IMAGE_GIF);
        EXTENSION_MAP.put("webp", IMAGE_WEBP);
        EXTENSION_MAP.put("svg", IMAGE_SVG);

        for (ContentType type : values()) {
            MIME_MAP.put(type.value, type);
        }
    }

    public static ContentType fromExtension(String extension) {
        if (extension == null) return UNKNOWN;
        return EXTENSION_MAP.getOrDefault(extension.toLowerCase(), UNKNOWN);
    }

    public static ContentType fromMime(String mime) {
        if (mime == null) return UNKNOWN;
        return MIME_MAP.getOrDefault(mime.toLowerCase(), UNKNOWN);
    }
}
