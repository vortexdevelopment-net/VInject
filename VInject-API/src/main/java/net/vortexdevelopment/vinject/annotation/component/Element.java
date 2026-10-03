package net.vortexdevelopment.vinject.annotation.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated class can be collected for a collection of elements by the framework.
 * Respects conditional loading.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Element {
    
    /**
     * Priority for ordering elements during collection.
     * Lower values are processed first.
     * Range: Integer.MIN_VALUE to Integer.MAX_VALUE
     * Default: 0
     * 
     * @return the priority value
     */
    int priority() default 0;
}
