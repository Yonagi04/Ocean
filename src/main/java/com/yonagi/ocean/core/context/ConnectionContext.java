package com.yonagi.ocean.core.context;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/17 15:34
 */
public class ConnectionContext {

    private final boolean isSsl;
    private final boolean sslEnabled;
    private final boolean redirectSslEnabled;
    private final int sslPort;
    private final ServerContext serverContext;

    public ConnectionContext(boolean isSsl, boolean sslEnabled, boolean redirectSslEnabled, int sslPort, ServerContext serverContext) {
        this.isSsl = isSsl;
        this.sslEnabled = sslEnabled;
        this.redirectSslEnabled = redirectSslEnabled;
        this.sslPort = sslPort;
        this.serverContext = serverContext;
    }

    public boolean isSsl() {
        return isSsl;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public boolean isRedirectSslEnabled() {
        return redirectSslEnabled;
    }

    public int getSslPort() {
        return sslPort;
    }

    public ServerContext getServerContext() {
        return serverContext;
    }
}
