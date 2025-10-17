package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 13:10
 */
public class MiddlewareChain {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareChain.class);

    private final List<Middleware> middlewares = new ArrayList<>();
    private int currentIndex = 0;
    private HttpResponse latestResponse;

    public void addMiddleWare(Middleware middleware) {
        this.middlewares.add(middleware);
    }

    public void setResponse(HttpResponse response) {
        this.latestResponse = response;
    }

    public HttpResponse getResponse() {
        return this.latestResponse;
    }

    public HttpResponse execute(HttpRequest request, HttpResponse response) {
        this.latestResponse = response;
        try {
            while (currentIndex < middlewares.size()) {
                Middleware current = middlewares.get(currentIndex++);
                boolean shouldContinue = current.handle(request, this);
                if (!shouldContinue) {
                    if (latestResponse == response) {
                        log.warn("Middleware {} interrupted but did not set response.", current.getClass().getSimpleName());
                        latestResponse = new HttpResponse.Builder()
                                .httpVersion(request.getHttpVersion())
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .contentType("text/plain; charset=utf-8")
                                .body("Middleware interrupted request".getBytes())
                                .build();
                    }
                    return latestResponse;
                }
            }
        } catch (Exception e) {
            log.error("Unhandled exception in middleware: {}", e.getMessage(), e);
            return new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType("text/plain; charset=utf-8")
                    .body("Internal Server Error".getBytes())
                    .build();
        }
        return latestResponse;
    }
}
