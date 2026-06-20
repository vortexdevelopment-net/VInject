package net.vortexdevelopment.vinject.annotation.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for classes where instances need to be created and managed by the plugin.
 * <p>
 * The concrete class and all inherited super-types (classes and interfaces) are registered
 * automatically for injection. When multiple beans share the same type, use {@code name} or
 * {@link net.vortexdevelopment.vinject.annotation.Qualifier} on the producer and
 * {@link net.vortexdevelopment.vinject.annotation.Qualifier} on the injection point.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

    /**
     * Optional bean name for named injection via {@link net.vortexdevelopment.vinject.annotation.Qualifier}.
     */
    String name() default "";

    /**
     * Additional types to register this component under, beyond the automatically discovered
     * super-types. Each entry must be implemented or extended by this class.
     */
    public Class<?>[] registerSubclasses() default {};

    /**
     * Register priority. Lower numbers are loaded first.
     * @return The load priority of the component.
     */
    public int priority() default 10;
}
