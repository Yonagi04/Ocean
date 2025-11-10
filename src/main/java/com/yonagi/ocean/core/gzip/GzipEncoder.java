package com.yonagi.ocean.core.gzip;

import com.yonagi.ocean.core.gzip.config.GzipConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/13 11:35
 */
public class GzipEncoder {

    private volatile GzipConfig currentConfig;

    private static final Logger log = LoggerFactory.getLogger(GzipEncoder.class);

    public GzipEncoder(GzipConfig currentConfig) {
        this.currentConfig = currentConfig;
    }

    public void updateConfig(GzipConfig newConfig) {
        this.currentConfig = newConfig;
        log.info("GzipEncoder config updated: enabled={}, minLength={}, compressionLevel={}",
                currentConfig.isEnabled(), currentConfig.getMinContentLength(), currentConfig.getCompressionLevel());
    }

    public byte[] encode(byte[] responseBody, String acceptEncoding) {
        GzipConfig config = this.currentConfig;

        if (!config.isEnabled() || responseBody == null || responseBody.length < config.getMinContentLength()) {
            return responseBody;
        }
        if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
            return responseBody;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(bos, this.currentConfig.getCompressionLevel())) {
            gos.write(responseBody);
            gos.finish();
        } catch (IOException e) {
            log.error("Gzip compression failed: {}", e.getMessage(), e);
            return responseBody;
        }
        return bos.toByteArray();
    }
}
