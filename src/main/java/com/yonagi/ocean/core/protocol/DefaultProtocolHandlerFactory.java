package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.context.ConnectionContext;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.core.protocol.handler.impl.CorsPreflightProtocolHandler;
import com.yonagi.ocean.core.protocol.handler.impl.HstsProtocolHandler;
import com.yonagi.ocean.core.protocol.handler.impl.HttpsRedirectProtocolHandler;
import com.yonagi.ocean.core.protocol.handler.impl.RequestBodyReaderProtocolHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 16:30
 */
public class DefaultProtocolHandlerFactory implements ProtocolHandlerFactory {

    @Override
    public List<HttpProtocolHandler> createHandlers(ConnectionContext connectionContext) {
        List<HttpProtocolHandler> handlers = new ArrayList<>();

        if (!connectionContext.isSsl() && connectionContext.isSslEnabled() && connectionContext.isRedirectSslEnabled()) {
            handlers.add(new HttpsRedirectProtocolHandler(connectionContext.getSslPort()));
        }
        handlers.add(new RequestBodyReaderProtocolHandler());
        handlers.add(new HstsProtocolHandler(connectionContext.isSsl()));
        handlers.add(new CorsPreflightProtocolHandler(connectionContext.isSsl()));

        return handlers;
    }
}
