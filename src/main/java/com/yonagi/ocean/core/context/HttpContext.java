package com.yonagi.ocean.core.context;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 09:16
 */
public class HttpContext {

    private HttpRequest request;
    private final AtomicReference<HttpResponse> responseRef;
    private final OutputStream output;
    private final boolean keepalive;
    private final ConnectionContext connectionContext;
    private final AtomicBoolean commited = new AtomicBoolean(false);

    public HttpContext(HttpRequest request, HttpResponse response, OutputStream output,
                       boolean keepalive, ConnectionContext connectionContext) {
        this.request = request;
        this.responseRef = new AtomicReference<>(response);
        this.output = output;
        this.keepalive = keepalive;
        this.connectionContext = connectionContext;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public void setRequest(HttpRequest request) {
        this.request = request;
    }

    public HttpResponse getResponse() {
        return responseRef.get();
    }

    public void setResponse(HttpResponse response) {
        this.responseRef.set(response);
    }

    public OutputStream getOutput() {
        return output;
    }

    public boolean isKeepalive() {
        return keepalive;
    }

    public ConnectionContext getConnectionContext() {
        return connectionContext;
    }

    public boolean isCommited() {
        return commited.get();
    }

    public void commitResponse() {
        commited.set(true);
    }
}
