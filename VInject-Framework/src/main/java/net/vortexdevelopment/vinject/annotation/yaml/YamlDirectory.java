package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare a directory of YAML files to be batch-loaded into instances of a target class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface YamlDirectory {

    /**
     * Directory path relative to the ConfigurationContainer root directory (or absolute).
     */
    String dir();

    /**
     * The target class to map each YAML entry to. A serializer must be registered for this class.
     */
    Class<?> target();

    /**
     * If non-empty, load entries under this root key inside each file. If empty, files are expected
     * to contain top-level mappings where each key is an item id.
     */
    String rootKey() default "";

    /**
     * Whether to recurse into subdirectories.
     */
    boolean recursive() default true;

    /**
     * Whether to copy matching directory from jar resources if the disk directory is empty or doesn't exist.
     */
    boolean copyDefaults() default true;

    /**
     * Note: batch id is now generated automatically as <holder-class-fqcn>::<dir>
     */
}


