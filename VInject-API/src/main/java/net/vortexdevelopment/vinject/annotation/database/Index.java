package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a database index.
 *
 * <p>On a field, {@link #columns()} must be empty and that field is indexed. On an
 * entity class, {@code columns} must list one or more Java field names or physical
 * {@link Column#name()} values. Column order is preserved for composite indexes.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
@Repeatable(Indexes.class)
public @interface Index {

    String name() default "";

    String[] columns() default {};

    boolean unique() default false;
}
