package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a named bean for injection disambiguation (Spring-style qualifier).
 *
 * <p>On a {@link net.vortexdevelopment.vinject.annotation.component.Component} or {@link Bean}
 * producer, sets the bean name. On an injection point ({@link Inject} field, parameter, or setter),
 * selects the bean with that name.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface Qualifier {
    String value();
}
