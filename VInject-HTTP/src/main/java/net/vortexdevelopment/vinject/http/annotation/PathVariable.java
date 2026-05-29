package net.vortexdevelopment.vinject.http.annotation;

import java.lang.annotation.*;

/**
 * Annotation which indicates that a method parameter should be bound to a URI template path variable.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PathVariable {
    /**
     * The name of the path variable to bind to.
     * If not specified, the parameter name is used as the path variable name.
     */
    String value() default "";
}
