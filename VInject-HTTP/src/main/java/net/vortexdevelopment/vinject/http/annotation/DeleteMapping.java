package net.vortexdevelopment.vinject.http.annotation;

import java.lang.annotation.*;

/**
 * Annotation for mapping HTTP DELETE requests onto specific handler methods.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DeleteMapping {
    /**
     * The path mapping URIs (e.g. "/myPath").
     */
    String value() default "";
}
