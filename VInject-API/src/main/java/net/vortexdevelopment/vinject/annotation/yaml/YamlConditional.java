package net.vortexdevelopment.vinject.annotation.yaml;

import net.vortexdevelopment.vinject.di.utils.ConditionalOperator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated component should only be loaded if a specific condition
 * in the YAML configuration is met. The condition is defined by checking a specific
 * path in the YAML configuration against an expected value.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface YamlConditional {

    /**
     * The configuration class that contains the YAML settings to check against.
     *
     * @return The configuration class.
     */
    Class<?> configuration();

    /**
     * The path within the YAML configuration to check for the condition.
     *
     * @return The YAML path as a string.
     */
    String path();

    /**
     * The expected value at the given path for this condition to be met.
     *
     * @return The expected value as a string.
     */
    String value() default "true";

    ConditionalOperator operator() default ConditionalOperator.EQUALS;
}
