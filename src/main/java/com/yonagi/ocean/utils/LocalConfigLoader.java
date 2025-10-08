package com.yonagi.ocean.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description 通用配置加载类
 * @date 2025/10/05 10:39
 */
public class LocalConfigLoader {
    private static final Properties properties = new Properties();

    static {
        try (InputStream inputStream = LocalConfigLoader.class.getResourceAsStream("/server.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            } else {
                throw new RuntimeException("配置文件未找到：/server.properties");
            }
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件时出错: "+ e.getMessage(), e);
        }
    }

    private LocalConfigLoader() {}

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
