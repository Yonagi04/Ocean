package com.yonagi.ocean.handler;

import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.HttpResponse;
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
public class InternalErrorHandler {

    private final String errorPagePath;

    private static final String DEFAULT_500_HTML = "<html>\n" +
            "<head>\n" +
            "    <title>Error</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            width: 35em;\n" +
            "            margin: 0 auto;\n" +
            "            font-family: Tahoma, Verdana, Arial, sans-serif;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>An error occurred.</h1>\n" +
            "<p>Sorry, the page you are looking for is currently unavailable.<br/>\n" +
            "    Please try again later.</p>\n" +
            "<p>If you are the system administrator of this resource then you should check\n" +
            "    the error log for details.</p>\n" +
            "</body>\n" +
            "</html>";

    private static final Logger log = LoggerFactory.getLogger(InternalErrorHandler.class);
    private final StaticFileCache fileCache = StaticFileCacheFactory.getInstance();

    public InternalErrorHandler() {
        this.errorPagePath = LocalConfigLoader.getProperty("server.internal_error_page");
    }

    public void handleInternalError(OutputStream outputStream) {
        File errorPage = new File(errorPagePath);
        if (errorPage.exists()) {
            try {
                handle(outputStream);
            } catch (Exception ex) {
                log.error("Error in handle method: {}", ex.getMessage(), ex);
                ex.printStackTrace();
            }
        } else {
            log.info("Error page not found, using default error response");
            writeDefaultErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }

    private void handle(OutputStream outputStream) {
        File errorPage = new File(errorPagePath);
        String contentType = MimeTypeUtil.getMimeType(errorPage.getName());
        if (contentType == null) {
            contentType = "text/html";
        }
        try {
            CachedFile cf = fileCache.get(errorPage);
            HttpResponse httpResponse = new HttpResponse.Builder()
                    .httpVersion("HTTP/1.1")
                    .statusCode(500)
                    .statusText("Internal Server Error")
                    .contentType(contentType)
                    .body(cf.getContent())
                    .build();
            outputStream.write(httpResponse.toString().getBytes());
            outputStream.flush();
        } catch (Exception e) {
            log.error("Error serving internal error page: {}", e.getMessage(), e);
            writeDefaultErrorResponse(outputStream, 500, "Internal Server Error");
        }
    }

    private void writeDefaultErrorResponse(OutputStream outputStream, int code, String message) {
        try {
            HttpResponse httpResponse = new HttpResponse.Builder()
                    .httpVersion("HTTP/1.1")
                    .statusCode(code)
                    .statusText(message)
                    .contentType("text/html")
                    .body(DEFAULT_500_HTML.getBytes())
                    .build();
            outputStream.write(httpResponse.toString().getBytes());
            outputStream.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
