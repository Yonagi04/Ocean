package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/10 16:00
 */
public class TooManyRequestsHandler implements RequestHandler {

    private final String errorPagePath;

    private static final String DEFAULT_429_HTML = "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>请求过多</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            background: linear-gradient(135deg, #fbc2eb 0%, #a6c1ee 100%);\n" +
            "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
            "            margin: 0;\n" +
            "            height: 100vh;\n" +
            "            display: flex;\n" +
            "            align-items: center;\n" +
            "            justify-content: center;\n" +
            "        }\n" +
            "        .error-container {\n" +
            "            background: #fff;\n" +
            "            padding: 2.5em 3em;\n" +
            "            border-radius: 16px;\n" +
            "            box-shadow: 0 8px 32px rgba(44, 62, 80, 0.15);\n" +
            "            text-align: center;\n" +
            "        }\n" +
            "        .error-code {\n" +
            "            font-size: 4em;\n" +
            "            color: #9b59b6;\n" +
            "            font-weight: bold;\n" +
            "            margin-bottom: 0.2em;\n" +
            "        }\n" +
            "        .error-message {\n" +
            "            font-size: 1.3em;\n" +
            "            color: #333;\n" +
            "            margin-bottom: 1em;\n" +
            "        }\n" +
            "        .tips {\n" +
            "            color: #888;\n" +
            "            font-size: 1em;\n" +
            "        }\n" +
            "        .admin-tips {\n" +
            "            margin-top: 1em;\n" +
            "            font-size: 0.95em;\n" +
            "            color: #b71c1c;\n" +
            "        }\n" +
            "        .home-link {\n" +
            "            display: inline-block;\n" +
            "            margin-top: 1.5em;\n" +
            "            padding: 0.6em 1.2em;\n" +
            "            background: #9b59b6;\n" +
            "            color: #fff;\n" +
            "            border-radius: 8px;\n" +
            "            text-decoration: none;\n" +
            "            transition: background 0.2s;\n" +
            "        }\n" +
            "        .home-link:hover {\n" +
            "            background: #6d399b;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"error-container\">\n" +
            "        <div class=\"error-code\">429</div>\n" +
            "        <div class=\"error-message\">请求过多，服务器暂时无法处理您的请求。</div>\n" +
            "        <div class=\"tips\">请稍后再试，或返回首页。</div>\n" +
            "        <div class=\"admin-tips\">如您是管理员，请检查服务器限流设置。</div>\n" +
            "        <a class=\"home-link\" href=\"/index.html\">返回首页</a>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";

    private static final Logger log = LoggerFactory.getLogger(TooManyRequestsHandler.class);
    private final StaticFileCache fileCache = StaticFileCacheFactory.getInstance();

    public TooManyRequestsHandler() {
        this.errorPagePath = LocalConfigLoader.getProperty("server.too_many_requests_page", "./www/429.html");
    }

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true);
    }

    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        File errorPage = new File(errorPagePath);
        if (errorPage.exists()) {
            try {
                String contentType = MimeTypeUtil.getMimeType(errorPage.getName());
                if (contentType == null) {
                    contentType = "text/html";
                }
                CachedFile cf = fileCache.get(errorPage);
                HttpResponse httpResponse = new HttpResponse.Builder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(contentType)
                        .body(cf.getContent())
                        .build();
                httpResponse.write(output, keepAlive);
                output.flush();
            } catch (Exception e) {
                log.error("Error serving too many requests page: {}", e.getMessage(), e);
                writeDefaultResponse(output, keepAlive);
            }
        } else {
            log.info("Error Page not found, using default error response");
            writeDefaultResponse(output, keepAlive);
        }
    }

    private void writeDefaultResponse(OutputStream output, boolean keepAlive) {
        try {
            HttpResponse httpResponse = new HttpResponse.Builder()
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .httpStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType("text/html")
                    .body(DEFAULT_429_HTML.getBytes())
                    .build();
            httpResponse.write(output, keepAlive);
            output.flush();
        } catch (Exception ignored) {

        }
    }
}
