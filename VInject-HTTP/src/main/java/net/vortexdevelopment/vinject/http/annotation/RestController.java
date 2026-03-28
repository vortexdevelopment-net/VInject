package net.vortexdevelopment.vinject.http.annotation;

import net.vortexdevelopment.vinject.annotation.component.Component;
import java.lang.annotation.*;

/**
 * Indicates that returning values of methods will be bound to the web response body.
 * Marks the class as a web controller and a VInject component.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RestController {
    /**
     * Path prefix for all endpoints in this controller
     */
    String value() default "";
}
