package com.yonagi.ocean.core.protocol.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
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

    public static byte[] readTextBodyFromInputStream(InputStream rawInputStream, Map<String, String> headers) throws IOException {
        if (rawInputStream == null) {
            return new byte[0];
        }
        String contentLengthStr = headers.get("content-length");
        int contentLength = contentLengthStr == null ? 0 : Integer.parseInt(contentLengthStr);

        if (contentLength <= 0) {
            return new byte[0];
        }

        Charset charset = StandardCharsets.UTF_8;
        String contentType = headers.get("content-type");
        if (contentType != null) {
            try {
                String[] parts = contentType.split(";");
                for (String part : parts) {
                    String trimmedPart = part.trim().toLowerCase();
                    if (trimmedPart.startsWith("charset=")) {
                        String charsetName = trimmedPart.substring("charset=".length()).trim();
                        charset = Charset.forName(charsetName);
                        break;
                    }
                }
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                log.warn("Unsupported charset in Content-Type: {}, defaulting to UTF-8.", contentType);
            }
        }

        InputStreamReader isr = new InputStreamReader(rawInputStream, charset);
        BufferedReader reader = new BufferedReader(isr);

        CharArrayWriter writer = new CharArrayWriter(contentLength);
        char[] buffer = new char[1024];
        int remaining = contentLength;

        while (remaining > 0) {
            int read = reader.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new IOException("Stream ended prematurely. Excepted " + contentLength + " bytes.");
            }
            writer.write(buffer, 0, read);
            remaining -= read;
        }
        return writer.toString().getBytes(charset);
    }
}
