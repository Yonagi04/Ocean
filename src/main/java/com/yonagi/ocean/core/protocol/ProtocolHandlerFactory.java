package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 16:29
 */
public interface ProtocolHandlerFactory {

    List<HttpProtocolHandler> createHandlers(
            boolean isSsl,
            boolean sslEnabled,
            boolean redirectSslEnabled,
            int sslPort
    );
}
