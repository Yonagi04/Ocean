package com.yonagi.ocean.middleware;

import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/17 11:47
 */
public class MiddlewareLoader {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareLoader.class);
    private static final String DEFAULT_MIDDLEWARE_PACKAGE = "com.yonagi.ocean.middleware";

    public static List<Middleware> loadMiddlewares() {
        List<Middleware> middlewares = new ArrayList<>();
        Reflections reflections = new Reflections(DEFAULT_MIDDLEWARE_PACKAGE);

        Set<Class<? extends Middleware>> middlewareClasses = reflections.getSubTypesOf(Middleware.class);
        for (Class<? extends Middleware> middlewareClass : middlewareClasses) {
            try {
                Middleware instance = middlewareClass.getDeclaredConstructor().newInstance();
                middlewares.add(instance);
            } catch (Exception e) {
                log.error("Failed to initialize middleware: {}, {}", middlewareClass.getSimpleName(), e.getMessage(), e);
            }
        }
        middlewares.sort(Comparator.comparingInt(MiddlewareLoader::getPriority));
        log.info("Loaded {} middleware(s)", middlewares.size());
        return middlewares;
    }

    private static int getPriority(Middleware middleware) {
        MiddlewarePriority p = middleware.getClass().getAnnotation(MiddlewarePriority.class);
        return p != null ? p.value() : 0;
    }
}
