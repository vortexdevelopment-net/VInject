package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Bean {

    /**
     * Optional bean name for named injection via {@link Qualifier}.
     */
    String name() default "";

    /**
     * Additional types to register the bean under, beyond automatically discovered super-types.
     * Each entry must be assignable from the bean return type.
     */
    public Class<?>[] registerSubclasses() default {};
}
