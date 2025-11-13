package com.yonagi.ocean.core.protocol;

import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/12 19:49
 */
public class Attribute {

    private String clientIp;

    private Boolean isSsl;

    private Map<String, String> corsResponseHeaders;

    private Map<String, String> hstsHeaders;

    private Map<String, String> pathVariableAttributes;

    private String targetUrl;

    private Integer redirectStatusCode;

    private String redirectContentType;

    private Boolean isSetCookie;

    private String sessionId;

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Boolean getSsl() {
        return isSsl;
    }

    public void setSsl(Boolean ssl) {
        isSsl = ssl;
    }

    public Map<String, String> getCorsResponseHeaders() {
        return corsResponseHeaders;
    }

    public void setCorsResponseHeaders(Map<String, String> corsResponseHeaders) {
        this.corsResponseHeaders = corsResponseHeaders;
    }

    public Map<String, String> getHstsHeaders() {
        return hstsHeaders;
    }

    public void setHstsHeaders(Map<String, String> hstsHeaders) {
        this.hstsHeaders = hstsHeaders;
    }

    public Map<String, String> getPathVariableAttributes() {
        return pathVariableAttributes;
    }

    public void setPathVariableAttributes(Map<String, String> pathVariableAttributes) {
        this.pathVariableAttributes = pathVariableAttributes;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public Integer getRedirectStatusCode() {
        return redirectStatusCode;
    }

    public void setRedirectStatusCode(Integer redirectStatusCode) {
        this.redirectStatusCode = redirectStatusCode;
    }

    public String getRedirectContentType() {
        return redirectContentType;
    }

    public void setRedirectContentType(String redirectContentType) {
        this.redirectContentType = redirectContentType;
    }

    public Boolean getSetCookie() {
        return isSetCookie;
    }

    public void setSetCookie(Boolean setCookie) {
        isSetCookie = setCookie;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
