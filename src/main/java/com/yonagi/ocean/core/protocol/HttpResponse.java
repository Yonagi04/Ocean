package com.yonagi.ocean.core.protocol;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 16:24
 */
public class HttpResponse {
    private HttpVersion httpVersion;
    private Integer statusCode;
    private String statusText;
    private String contentType;
    private byte[] body;

    private HttpResponse() {

    }

    public HttpVersion getHttpVersion() {
        return httpVersion;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public String toString() {
        return httpVersion.getVersion() + " " + statusCode + " " + statusText + "\r\n" +
               "Content-Type: " + contentType + "\r\n" +
               "Content-Length: " + (body != null ? body.length : 0) + "\r\n" +
               "\r\n" +
               (body != null ? new String(body) : "");
    }

    public static class Builder {
        private HttpVersion httpVersion;
        private Integer statusCode;
        private String statusText;
        private String contentType;
        private byte[] body;

        public Builder httpVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder statusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder statusText(String statusText) {
            this.statusText = statusText;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public HttpResponse build() {
            if (statusCode == null || statusText == null || contentType == null) {
                throw new IllegalStateException("Status code, status text, and content type must be set");
            }
            HttpResponse httpResponse = new HttpResponse();
            httpResponse.httpVersion = this.httpVersion;
            httpResponse.statusCode = this.statusCode;
            httpResponse.statusText = this.statusText;
            httpResponse.contentType = this.contentType;
            httpResponse.body = this.body;
            return httpResponse;
        }
    }
}
