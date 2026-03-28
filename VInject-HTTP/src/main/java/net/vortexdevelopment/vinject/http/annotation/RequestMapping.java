package net.vortexdevelopment.vinject.http.annotation;

import java.lang.annotation.*;

/**
 * Annotation for mapping web requests onto methods in request-handling classes.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {
    /**
     * The path mapping URIs (e.g. "/myPath").
     */
    String value() default "";

    /**
     * The HTTP method to map to (e.g. "GET", "POST").
     */
    String method() default "GET";
}
