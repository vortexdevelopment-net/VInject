package net.vortexdevelopment.vinject.annotation.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that should be called after the constructor completes
 * and dependency injection is finished. Methods annotated with @PostConstruct will be
 * invoked after all dependencies have been injected into the component.
 * These methods should have no parameters and return void.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostConstruct {
}

