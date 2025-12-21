package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for specifying method chains to extract values from serialized objects.
 * Used in RegisterDatabaseSerializer helper classes to automatically extract values via method calls.
 * 
 * Example: @MethodValue("getWorld.getName") will call getWorld().getName()
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface MethodValue {
    /**
     * MethodValue chain string (e.g., "getWorld.getName" to call getWorld().getName())
     * Methods are called in sequence, with each result passed to the next method.
     * 
     * @return the method chain
     */
    String value();
}
