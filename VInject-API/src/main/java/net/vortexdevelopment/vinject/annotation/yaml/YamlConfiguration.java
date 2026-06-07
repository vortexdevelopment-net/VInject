package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface YamlConfiguration {

    /**
     * The file path and name of the YAML configuration file.
     *
     * @return The file path and name separated by '/'.
     */
    String file();

    /**
     * The path within the YAML file to map to this configuration class.
     *
     * @return The path within the YAML file.
     */
    String path() default "";

    /**
     * Whether to automatically save changes to the YAML file when the configuration is modified.
     *
     * @return True if auto-save is enabled, false otherwise.
     */
    boolean autoSave() default false;

    /**
     * Whether to save the YAML configuration asynchronously.
     *
     * @return True if async save is enabled, false otherwise.
     */
    boolean asyncSave() default false;

    /**
     * Optional encoding to use when reading/writing the YAML file. Defaults to UTF-8.
     */
    String encoding() default "UTF-8";
}
