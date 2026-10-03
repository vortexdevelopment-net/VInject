package net.vortexdevelopment.vinject.annotation.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods called after a YAML config object or database entity has its fields
 * mapped. Methods annotated with @OnLoad are invoked after YAML/entity hydration - not as a
 * general @Component startup hook (use @PostConstruct for that).
 * These methods must return void. Parameters are optional and resolved from the container.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnLoad {
}
