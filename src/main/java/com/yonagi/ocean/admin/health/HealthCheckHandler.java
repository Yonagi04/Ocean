package com.yonagi.ocean.admin.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;

import java.io.IOException;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 18:45
 */
public class HealthCheckHandler implements RequestHandler {

    private final HealthCheckService healthCheckService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HealthCheckHandler(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HealthComponent result = healthCheckService.performHealthCheck();
        HttpStatus status = HttpStatus.fromCode(result.getStatus().getHttpStatusCode());

        String jsonBody = objectMapper.writeValueAsString(result);
        httpContext.setResponse(httpContext.getResponse().toBuilder()
                .httpVersion(httpContext.getRequest().getHttpVersion())
                .httpStatus(status)
                .contentType("application/json; charset=utf-8")
                .body(jsonBody.getBytes())
                .build());
    }
}
