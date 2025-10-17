package com.yonagi.ocean.framework.annotation;

import com.yonagi.ocean.core.protocol.enums.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequestMapping {

    String path();

    HttpMethod[] method() default {HttpMethod.GET};
}
