package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for specifying field paths to extract values from serialized objects.
 * Used in RegisterDatabaseSerializer helper classes to automatically extract nested field values.
 * 
 * Example: @FieldValue("world.name") will extract location.getWorld().getName()
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldValue {
    /**
     * FieldValue path string (e.g., "world.name" to get world.getName())
     * The path is navigated using field access or getter methods.
     * 
     * @return the field path
     */
    String value();
}
