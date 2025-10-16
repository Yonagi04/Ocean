package com.yonagi.ocean.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {

    String value() default "";

    boolean required() default true;

    String defaultValue() default NO_DEFAULT_VALUE;

    String NO_DEFAULT_VALUE = "\n\t\t\n\t\t\n\u0000\n\t\t\t\n\u0000\n\t\t\n\u0000\n\t\t\n\t\t\n";
}