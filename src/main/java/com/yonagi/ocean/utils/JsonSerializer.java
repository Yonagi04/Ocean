package com.yonagi.ocean.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/15 15:05
 */
public class JsonSerializer {

    private static final Logger log = LoggerFactory.getLogger(JsonSerializer.class);

    private static ObjectMapper mapper = new ObjectMapper();

    public static String serialize(Object object) {
        if (object == null) {
            return "{}";
        }
        return String.format("{\"type\":\"%s\", \"data\":\"%s\"}",
                object.getClass().getSimpleName(),
                object.toString().replaceAll("\"", "\\\"").replaceAll("\n", "\\n"));
    }


}
