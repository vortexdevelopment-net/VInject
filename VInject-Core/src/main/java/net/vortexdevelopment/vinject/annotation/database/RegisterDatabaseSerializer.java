package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for auto-discovery of database serializers.
 * Classes annotated with this must extend RegisterDatabaseSerializer<T> where T is the type being serialized.
 * 
 * Similar to @YamlSerializer annotation pattern.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegisterDatabaseSerializer {
    /**
     * The type this serializer handles (e.g., Location.class)
     * 
     * @return the class type being serialized
     */
    Class<?> value();
}
