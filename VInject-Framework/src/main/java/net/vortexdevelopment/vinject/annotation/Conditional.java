package net.vortexdevelopment.vinject.annotation;

import net.vortexdevelopment.vinject.di.utils.ConditionalOperator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Conditional {

    /**
     * Property name to check.
     *
     * @return The property name.
     */
    String property();

    /**
     * Expected value of the property.
     *
     * @return The expected value.
     */
    String value() default "true";

    /**
     * The operator to use for the condition.
     *
     * @return The conditional operator.
     */
    ConditionalOperator operator() default ConditionalOperator.EQUALS;

}
