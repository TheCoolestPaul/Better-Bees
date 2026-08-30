package com.betterbees.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface SharedGameTest {
    String template();
    int timeoutTicks() default 100;
    int setupTicks() default 0;
    boolean required() default true;
}
