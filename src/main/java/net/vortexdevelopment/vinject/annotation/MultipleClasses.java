package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to register multiple classes as components.
 * The classes must implement the same interface or extend the same class.
 * The classes must have a no-args constructor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface MultipleClasses {

    /**
     * Register the class as a subclass of the specified classes.
     * Usage: ChildClass implements ParentClass, Component(registerSubclasses = {ParentClass.class})
     * If the provided classes are not extended or implemented by the class error will be thrown.
     *
     * @return The classes to register the class as a subclass of.
     */
    Class<?>[] registerSubclasses() default {};
}
