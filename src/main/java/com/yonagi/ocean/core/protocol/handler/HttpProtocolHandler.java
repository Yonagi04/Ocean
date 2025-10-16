package com.yonagi.ocean.core.protocol.handler;

import com.yonagi.ocean.core.protocol.HttpRequest;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 14:58
 */
public interface HttpProtocolHandler {

    HttpRequest handle(HttpRequest request, OutputStream output) throws IOException;
}
