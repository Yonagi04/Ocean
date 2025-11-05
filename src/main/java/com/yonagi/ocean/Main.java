package com.yonagi.ocean;

import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.HttpServer;
import com.yonagi.ocean.utils.LocalConfigLoader;
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
        try {
            HttpServer server = new HttpServer();
            server.start();
            log.info("Ocean server started successfully. Press Ctrl+C to stop.");

            server.awaitShutdown();
            log.info("Ocean server has been shut down.");
        } catch (Exception e) {
            log.error("Failed to start Ocean server", e);
            System.exit(1);
        }
    }
}
