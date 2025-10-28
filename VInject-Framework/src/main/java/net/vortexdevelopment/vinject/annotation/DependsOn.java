package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotated components can declare dependencies with this annotation for classes they depend on.
 * If any of the specified classes are not present, the annotated component will not be initialized.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DependsOn {

    /**
     * A single class that the annotated class depends on.
     *
     * @return A Class object representing the dependency.
     */
    Class<?> value() default Void.class;

    /**
     * An array of classes that the annotated class depends on.
     *
     * @return An array of Class objects representing the dependencies.
     */
    Class<?>[] values() default {};

    /**
     * The name of the class that the annotated class depends on.
     * This can be used when the class may not be available at compile time.
     *
     * @return The fully qualified name of the dependency class.
     */
    String className() default "";

    /**
     * An array of class names that the annotated class depends on.
     * This can be used when the classes may not be available at compile time.
     *
     * @return An array of fully qualified names of the dependency classes.
     */
    String[] classNames() default {};
    /**
     * Whether the dependency is soft. If true (default) missing dependencies will cause the annotated class to be skipped.
     * If false, missing dependencies will cause an immediate runtime error.
     */
    boolean soft() default true;
}
