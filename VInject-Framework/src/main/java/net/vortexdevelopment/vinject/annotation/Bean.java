package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Bean {

    /**
     * Register the class as a subclass of the specified classes.
     * Usage: ChildClass implements ParentClass, Component(registerSubclasses = {ParentClass.class})
     * If the provided classes are not extended or implemented by the class error will be thrown.
     *
     * @return The classes to register the class as a subclass of.
     */
    public Class<?>[] registerSubclasses() default {};
}
