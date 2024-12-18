package net.vortexdevelopment.vinject.annotation;

import net.vortexdevelopment.vinject.di.registry.RegistryOrder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for classes that provides annotations for the framework.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Registry {

    RegistryOrder order() default RegistryOrder.COMPONENTS;

}
