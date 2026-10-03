package net.vortexdevelopment.vinject.annotation.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that should be called when the application is shutting down.
 * Methods annotated with @OnDestroy will be invoked on components during application shutdown.
 * These methods should have no parameters and return void.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnDestroy {
}

