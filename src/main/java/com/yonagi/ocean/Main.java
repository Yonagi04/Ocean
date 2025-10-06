package com.yonagi.ocean;

import com.yonagi.ocean.core.HttpServer;
import com.yonagi.ocean.utils.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description Ocean启动类
 * @date 2025/10/03 17:26
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        log.info("Ocean is starting...");

        String property = ConfigLoader.getProperty("server.port");
        String webRoot = ConfigLoader.getProperty("server.webroot");
        new HttpServer(Integer.parseInt(property), webRoot).start();
    }
}
