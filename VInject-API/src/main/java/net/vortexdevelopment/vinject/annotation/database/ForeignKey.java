package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a database foreign-key constraint for an entity field.
 *
 * <p>For scalar ID fields, {@link #entity()} is required. For entity-valued fields,
 * the referenced entity is inferred from the field type when {@code entity} is left
 * as {@code void.class}. A blank {@link #referencedColumn()} targets the referenced
 * entity's primary key. Explicit column references may use either the Java field name
 * or the physical name declared by {@link Column#name()}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForeignKey {

    Class<?> entity() default void.class;

    String referencedColumn() default "";

    String name() default "";

    ForeignKeyAction onDelete() default ForeignKeyAction.NO_ACTION;

    ForeignKeyAction onUpdate() default ForeignKeyAction.NO_ACTION;
}
