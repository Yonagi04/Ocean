package com.yonagi.ocean.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.exception.DeserializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/15 16:36
 */
public class JsonDeserializer {

    private static final Logger log = LoggerFactory.getLogger(JsonDeserializer.class);

    private static ObjectMapper mapper = new ObjectMapper();

    public static <T> T deserialize(byte[] bodyBytes, Class<T> targetType) throws DeserializationException, JsonProcessingException {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }

        String jsonString = new String(bodyBytes, StandardCharsets.UTF_8);
        if (targetType.isAssignableFrom(String.class)) {
            @SuppressWarnings("unchecked")
            T result = (T) jsonString;
            return result;
        }
        T result = mapper.readValue(jsonString, targetType);
        return result;
    }
}
