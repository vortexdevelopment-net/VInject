package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for classes where instances need to be created and managed by the plugin.
 */
@MultipleClasses
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

    /**
     * See {@link MultipleClasses#registerSubclasses()}
     */
    Class<?>[] registerSubclasses() default {};
}


