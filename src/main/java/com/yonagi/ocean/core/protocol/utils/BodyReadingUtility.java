package com.yonagi.ocean.core.protocol.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 14:59
 */
public class BodyReadingUtility {

    private static final Logger log = LoggerFactory.getLogger(BodyReadingUtility.class);

    public static byte[] readBodyFromInputStream(InputStream raw, Map<String, String> headers) throws IOException {
        if (raw == null) return new byte[0];

        String te = headers.get("transfer-encoding");
        if ("chunked".equalsIgnoreCase(te)) {
            return readChunkedBody(raw);
        }

        String cls = headers.get("content-length");
        if (cls != null && !cls.trim().isEmpty()) {
            try {
                int len = Integer.parseInt(cls.trim());
                if (len < 0) {
                    log.warn("Invalid Content-Length: {}", cls);
                    return new byte[0];
                }
                if (len == 0) {
                    return new byte[0];
                }
                
                // 使用 readNBytes 读取指定长度的数据
                // readNBytes 会阻塞直到读取到 len 字节或到达 EOF
                log.debug("Attempting to read {} bytes from input stream", len);
                byte[] body = raw.readNBytes(len);
                log.debug("Actually read {} bytes from input stream", body.length);
                
                // 验证读取到的数据长度
                if (body.length != len) {
                    if (body.length == 0) {
                        log.error("Failed to read request body: expected {} bytes but got 0 bytes. " +
                                "The input stream may have reached EOF prematurely.", len);
                        throw new IOException("Failed to read request body: expected " + len + 
                                            " bytes but got 0 bytes. Stream may have reached EOF prematurely.");
                    } else {
                        log.warn("Partial read: expected {} bytes but got {} bytes. " +
                                "The input stream may have reached EOF prematurely. " +
                                "This may indicate that the Content-Length header was incorrect or the connection was closed early.", 
                                len, body.length);
                        // 对于部分读取，我们仍然返回已读取的数据，但记录警告
                        // 这可能是由于网络问题或客户端提前关闭连接
                    }
                } else {
                    log.debug("Successfully read {} bytes, matching Content-Length header", body.length);
                    
                    // 检查输入流中是否还有剩余数据（这不应该发生，但可以帮助诊断问题）
                    // 注意：对于 SocketInputStream，available() 可能不准确，特别是对于网络流
                    // 但我们仍然尝试检查，以便发现明显的问题
                    try {
                        int available = raw.available();
                        if (available > 0) {
                            log.error("CRITICAL: After reading {} bytes (matching Content-Length: {}), " +
                                    "input stream still reports {} bytes available! " +
                                    "This suggests the Content-Length header was WRONG, or there is extra data. " +
                                    "The original request may have had a different Content-Length value.",
                                    body.length, len, available);
                            
                            // 尝试读取额外数据以验证
                            // 注意：这可能会阻塞，所以只在 DEBUG 模式下尝试
                            if (log.isDebugEnabled()) {
                                try {
                                    byte[] extraBytes = raw.readNBytes(Math.min(available, 100));
                                    if (extraBytes.length > 0) {
                                        log.error("CRITICAL: Found {} extra bytes in stream after reading body! " +
                                                "First few bytes: {}", extraBytes.length, 
                                                bytesToHex(extraBytes, Math.min(20, extraBytes.length)));
                                    }
                                } catch (Exception e) {
                                    log.debug("Could not read extra bytes: {}", e.getMessage());
                                }
                            }
                        } else {
                            log.debug("Input stream appears to be at EOF after reading {} bytes (as expected)", body.length);
                        }
                    } catch (IOException e) {
                        log.debug("Could not check available bytes in stream: {}", e.getMessage());
                    }
                }
                
                return body;
            } catch (NumberFormatException e) {
                log.warn("Invalid Content-Length header value: {}", cls, e);
                return new byte[0];
            }
        }
        return new byte[0];
    }

    private static byte[] readChunkedBody(InputStream raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (true) {
            String line = readLine(raw).trim();
            int chunkSize = Integer.parseInt(line, 16);

            if (chunkSize == 0) {
                readLine(raw);
                readLine(raw);
                break;
            }

            byte[] chunk = raw.readNBytes(chunkSize);
            out.write(chunk);

            // readLine(raw);
            int c1 = raw.read();
            int c2 = raw.read();
            if (c1 != '\r' || c2 != '\n') {
                throw new IOException("Invalid chunk ending");
            }
        }

        return out.toByteArray();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;

        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            baos.write(b);
        }

        String line = baos.toString(StandardCharsets.ISO_8859_1);
        if (line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }
    
    private static String bytesToHex(byte[] bytes, int maxLen) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(maxLen, bytes.length);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x ", bytes[i] & 0xFF));
        }
        return sb.toString().trim();
    }
}
