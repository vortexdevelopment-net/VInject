package net.vortexdevelopment.vinject.annotation.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to ignore the presence of {@code javax.inject.Inject} on a field or method.
 * By default, VInject will throw an exception if it detects the standard JSR-330 {@code @Inject} 
 * annotation, as it expects the custom {@link net.vortexdevelopment.vinject.annotation.Inject} instead.
 * 
 * <p>Use this if you are using a library that requires {@code javax.inject.Inject} or if you are
 * migrating code and need both annotations to coexist temporarily without triggering the safety check.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface IgnoreJavaxInject {
}
