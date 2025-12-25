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
     * Property name to check. Optional - if empty, only class presence will be checked.
     * If both property and class(es) are specified, both conditions must be met.
     *
     * @return The property name.
     */
    String property() default "";

    /**
     * A class to check if it is present. If the class is not present, the component will not be loaded.
     * Can be used alone or in combination with property checks.
     *
     * @return The class to check for presence.
     */
    Class<?> clazz() default Void.class;

    /**
     * Multiple classes to check if they are present. All classes must be present for the component to load.
     * Can be used alone or in combination with property checks.
     *
     * @return The classes to check for presence.
     */
    Class<?>[] classes() default {};

    /**
     * The name of the class to check if it is present.
     * This can be used when the class may not be available at compile time.
     * Use this instead of {@link #clazz()} for optional dependencies that might not be on the classpath.
     *
     * @return The fully qualified name of the class to check for presence.
     */
    String className() default "";

    /**
     * An array of class names to check if they are present. All classes must be present for the component to load.
     * This can be used when the classes may not be available at compile time.
     * Use this instead of {@link #classes()} for optional dependencies that might not be on the classpath.
     *
     * @return An array of fully qualified names of the classes to check for presence.
     */
    String[] classNames() default {};

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
