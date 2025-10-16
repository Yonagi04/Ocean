package com.yonagi.ocean.core.protocol;

import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.core.protocol.handler.impl.CorsPreflightProtocolHandler;
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
    public List<HttpProtocolHandler> createHandlers(boolean isSsl, boolean sslEnabled, boolean redirectSslEnabled, int sslPort) {
        List<HttpProtocolHandler> handlers = new ArrayList<>();

        if (!isSsl && sslEnabled && redirectSslEnabled) {
            handlers.add(new HttpsRedirectProtocolHandler(sslPort));
        }
        handlers.add(new RequestBodyReaderProtocolHandler());
        handlers.add(new CorsPreflightProtocolHandler(isSsl));

        return handlers;
    }
}
