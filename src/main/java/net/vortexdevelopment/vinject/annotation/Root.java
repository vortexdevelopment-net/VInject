package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Annotation to mark the main class of the plugin
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Root {
    String packageName();
}
