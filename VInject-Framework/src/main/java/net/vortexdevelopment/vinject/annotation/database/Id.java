package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a field as a primary key with auto-increment.
 * This is a convenience annotation equivalent to @Column(primaryKey = true, autoIncrement = true).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {
}
