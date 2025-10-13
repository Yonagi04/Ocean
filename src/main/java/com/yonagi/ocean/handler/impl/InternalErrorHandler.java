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
import java.io.OutputStream;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 16:01
 */
public class InternalErrorHandler implements RequestHandler {

    private final String errorPagePath;

    private static final String DEFAULT_500_HTML = "<!DOCTYPE html>\n" +
            "<html lang=\"zh-CN\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>服务器错误</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            background: linear-gradient(135deg, #f8ffae 0%, #43c6ac 100%);\n" +
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
            "            color: #e67e22;\n" +
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
            "            background: #e67e22;\n" +
            "            color: #fff;\n" +
            "            border-radius: 8px;\n" +
            "            text-decoration: none;\n" +
            "            transition: background 0.2s;\n" +
            "        }\n" +
            "        .home-link:hover {\n" +
            "            background: #b75d1c;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"error-container\">\n" +
            "        <div class=\"error-code\">内部错误</div>\n" +
            "        <div class=\"error-message\">服务器遇到错误，暂时无法处理您的请求。</div>\n" +
            "        <div class=\"tips\">请稍后再试，或返回首页。</div>\n" +
            "        <div class=\"admin-tips\">如您是管理员，请检查服务器日志以获取详细信息。</div>\n" +
            "        <a class=\"home-link\" href=\"/index.html\">返回首页</a>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";

    private static final Logger log = LoggerFactory.getLogger(InternalErrorHandler.class);

    public InternalErrorHandler() {
        this.errorPagePath = LocalConfigLoader.getProperty("server.internal_error_page");
    }

    @Override
    public void handle(HttpRequest request, OutputStream outputStream) {
        handle(request, outputStream, true); // Default to keep-alive
    }
    
    @Override
    public void handle(HttpRequest request, OutputStream outputStream, boolean keepAlive) {
        StaticFileCache fileCache = StaticFileCacheFactory.getInstance();
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
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(contentType)
                        .body(cf.getContent())
                        .build();
                httpResponse.write(request, outputStream, keepAlive);
                outputStream.flush();
            } catch (Exception ex) {
                log.error("Error serving internal error page: {}", ex.getMessage(), ex);
                writeDefaultErrorResponse(request, outputStream, keepAlive);
            }
        } else {
            log.info("Error page not found, using default error response");
            writeDefaultErrorResponse(request, outputStream, keepAlive);
        }
    }
    
    private void writeDefaultErrorResponse(HttpRequest request, OutputStream outputStream, boolean keepAlive) {
        try {
            HttpResponse httpResponse = new HttpResponse.Builder()
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType("text/html")
                    .body(DEFAULT_500_HTML.getBytes())
                    .build();
            httpResponse.write(request, outputStream, keepAlive);
            outputStream.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
