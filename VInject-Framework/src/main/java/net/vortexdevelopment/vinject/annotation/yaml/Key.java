package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {

    /**
     * The full path to the key in the YAML file. When the @YamlConfiguration has a base path, this path is relative to that.
     *
     * @return The YAML key path.
     */
    String value();
}
