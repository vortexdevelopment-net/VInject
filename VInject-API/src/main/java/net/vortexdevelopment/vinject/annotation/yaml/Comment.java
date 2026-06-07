package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Comment {

    /**
     * The comment lines to be added above the YAML element.
     *
     * @return An array of comment lines.
     */
    String[] value();
}
