package com.yonagi.ocean.framework;

import com.yonagi.ocean.framework.annotation.Controller;
import com.yonagi.ocean.framework.annotation.RequestMapping;
import com.yonagi.ocean.core.router.config.RouteConfig;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.router.RouteType;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.handler.RequestHandler;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/14 16:25
 */
public class ControllerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ControllerRegistry.class);

    private final Router router;
    private final String basePackage;

    public ControllerRegistry(Router router, String basePackage) {
        this.router = router;
        this.basePackage = basePackage;
    }

    public void scanAndRegister() {
        List<Class<?>> controllerClasses;
        try {
            ClassGraph classGraph = new ClassGraph()
                    .enableClassInfo()
                    .enableAnnotationInfo();
            if (this.basePackage.isEmpty()) {
                log.info("Starting global Classpath scan for @Controller.");
            } else {
                log.info("Starting limited Classpath scan in package: [{}] for @Controller.", this.basePackage);
                classGraph.whitelistPackages(this.basePackage);
            }
            try (ScanResult scanResult = classGraph.scan()) {
                controllerClasses = scanResult.getClassesWithAnnotation(Controller.class.getName())
                        .loadClasses() // 加载找到的类
                        .stream()
                        .collect(Collectors.toList());
            }
            log.info("Found {} controller classes in the classpath.", controllerClasses.size());
        } catch (Exception e) {
            log.error("Failed to perform classpath scanning for controllers.", e);
            return;
        }
        processAndRegisterControllers(controllerClasses);
    }

    private void processAndRegisterControllers(List<Class<?>> controllerClasses) {
        for (Class<?> controlerClass : controllerClasses) {
            if (controlerClass.isAnnotationPresent(Controller.class)) {
                try {
                    Object controllerInstance = controlerClass.getDeclaredConstructor().newInstance();
                    String handlerClassName = controlerClass.getSimpleName();

                    for (Method method : controlerClass.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(RequestMapping.class)) {
                            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                            if (mapping.method() == null) {
                                log.error("{}'s method is null", controlerClass.getSimpleName());
                                return;
                            }
                            Object handler = new MethodInvokingHandler(controllerInstance, method);
                            for (HttpMethod httpMethod : mapping.method()) {
                                RouteConfig config = new RouteConfig.Builder()
                                        .withEnabled(true)
                                        .withMethod(httpMethod)
                                        .withPath(mapping.path())
                                        .withHandlerClassName(MethodInvokingHandler.class.getName())
                                        .withContentType("text/html")
                                        .withRouteType(RouteType.CONTROLLER)
                                        .build();
                                router.registerRoute(config, (RequestHandler) handler);
                                log.info("Registered: {} {} -> {}.{}",
                                        httpMethod, mapping.path(), handlerClassName, method.getName());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process controller {}: {}", controlerClass.getName(), e.getMessage(), e);
                }
            }
        }
    }
}
