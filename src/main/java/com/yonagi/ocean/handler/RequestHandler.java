package com.yonagi.ocean.handler;

import com.alibaba.nacos.shaded.com.google.protobuf.ByteString;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:27
 */
public interface RequestHandler {

    void handle(HttpContext httpContext) throws IOException;
}
