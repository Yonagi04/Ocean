package com.yonagi.ocean.framework.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 11:31
 */
public class FormBinder {

    private static final Logger log = LoggerFactory.getLogger(FormBinder.class);

    public static Object bind(Map<String, String> formParams, Class<?> targetType) {
        if (formParams == null || formParams.isEmpty()) {
            try {
                return targetType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to instantiate targetType {} with default constructor.", targetType.getName(), e);
                return null;
            }
        }
        if (targetType == String.class) {
            return formParams.toString();
        }

        Object instance = null;
        try {
            instance = targetType.getDeclaredConstructor().newInstance();

            for (Map.Entry<String, String> entry : formParams.entrySet()) {
                String fieldName = entry.getKey();
                String value = entry.getValue();

                try {
                    Field field = targetType.getDeclaredField(fieldName);
                    Class<?> fieldType = field.getType();

                    Object convertedValue = convertValue(value, fieldType);
                    field.setAccessible(true);
                    field.set(instance, convertedValue);
                } catch (NoSuchFieldException e) {
                    log.debug("Form parameter '{}' not found in POJO fields of type {}. Skipping", fieldName, targetType.getSimpleName());
                } catch (Exception e) {
                    log.warn("Failed to bind form parameter '{}' with value '{}' to field in {}: {}", fieldName, value, targetType.getSimpleName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to instantiate or initialize POJO {} during Form Binding.", targetType.getName(), e);
            return null;
        }
        return instance;
    }

    private static Object convertValue(String value, Class<?> targetType) {
        if (value == null || value.isEmpty()) {
            return targetType.isPrimitive() ? getDefaultPrimitiveValue(targetType) : null;
        }
        if (targetType == String.class) {
            return value;
        }

        try {
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            } else if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            } else if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(value);
            } else if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value);
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (targetType == char.class || targetType == Character.class) {
                return Character.valueOf(value.charAt(0));
            } else if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(value);
            } else if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(value);
            }
        } catch (NumberFormatException e) {
            log.warn("Type conversion failed for value '{}' to target type '{}'", value, targetType.getSimpleName());
            return targetType.isPrimitive() ? getDefaultPrimitiveValue(targetType) : null;
        }
        // TODO: 复杂类型转换支持
        log.warn("Unsupported field type {} for form binding. Returning null.", targetType.getSimpleName());
        return null;
    }

    private static Object getDefaultPrimitiveValue(Class<?> type) {
        if (type == int.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == char.class) {
            return '\u0000';
        }
        return null;
    }
}
