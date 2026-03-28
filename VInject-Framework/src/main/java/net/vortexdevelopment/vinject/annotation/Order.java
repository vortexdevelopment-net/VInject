package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to define the order of execution for components like Filters.
 * Lower values have higher priority.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Order {
    /**
     * The order value. Default is 0.
     * Higher values mean lower priority (executed later).
     */
    int value() default 0;
}
