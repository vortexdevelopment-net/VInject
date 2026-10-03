package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark an entity field for proactive caching.
 * The value defines the namespace (e.g. CacheNamespaces.PLAYER_UUID).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoLoad {
    /**
     * The namespace identifier.
     */
    String value();
}
