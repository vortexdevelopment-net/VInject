package net.vortexdevelopment.vinject.annotation.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for classes where instances need to be created and managed by the plugin.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

    /**
     * Register the class as a subclass of the specified classes.
     * Usage: ChildClass implements ParentClass, Component(registerSubclasses = {ParentClass.class})
     * If the provided classes are not extended or implemented by the class error will be thrown.
     *
     * @return The classes to register the class as a subclass of.
     */
    public Class<?>[] registerSubclasses() default {};

    /**
     * Register priority. Lower numbers are loaded first.
     * @return The load priority of the component.
     */
    public int priority() default 10;
}
