package com.yonagi.ocean.utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 17:44
 */
public class UUIDUtil {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom random = new SecureRandom();

    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    public static String getUUIDWithoutDash() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getShortUUID() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        StringBuilder shortUuid = new StringBuilder();
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

        for (int i = 0; i < 8; i++) {
            String part = uuid.substring(i * 4, i * 4 + 4);
            int x = Integer.parseInt(part, 16);
            shortUuid.append(chars.charAt(x % 62));
        }
        return shortUuid.toString();
    }

    public static String getShortUUIDWithPrefix(String prefix) {
        return prefix + getShortUUID();
    }

    public static String getTimeBasedShortUUID() {
        long timestamp = Instant.now().toEpochMilli();
        String timePart = toBase62(timestamp);
        StringBuilder randomPart = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            randomPart.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return timePart + randomPart;
    }

    private static String toBase62(long value) {
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(BASE62.charAt((int) (value % 62)));
            value /= 62;
        }
        return sb.reverse().toString();
    }

    public static String getTimeBasedShortUUIDWithPrefix(String prefix) {
        return prefix + getTimeBasedShortUUID();
    }

}
