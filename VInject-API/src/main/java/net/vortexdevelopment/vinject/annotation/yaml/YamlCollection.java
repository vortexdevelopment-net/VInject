package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a collection field inside a @YamlDirectory holder class to be populated
 * with the loaded batch entries. Optional value specifies the batch id.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface YamlCollection {
    /**
     * Optional batch id (defaults to the directory path or annotation id).
     */
    String value() default "";
}


