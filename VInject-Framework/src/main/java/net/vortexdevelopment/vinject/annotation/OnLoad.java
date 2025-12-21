package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that should be called after an entity is loaded from the database
 * and all fields have been set. Methods annotated with @OnLoad will be invoked after all
 * database fields have been mapped to the entity instance.
 * These methods should return void and can optionally accept parameters for dependency injection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnLoad {
}
