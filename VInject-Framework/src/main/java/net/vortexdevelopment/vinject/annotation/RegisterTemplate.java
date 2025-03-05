package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to register a template for an annotation processor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RegisterTemplate {

    /**
     * @return the path to the template resource from the resource root
     */
    String resource();

    /**
     * @return the fully qualified class name of the annotation that has this template
     */
    String annotationFqcn();
}
