package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for fields with serializers to enable prefixing of serialized column names.
 * When present, serialized column names will be prefixed with the field's column name + "_".
 * When absent, serialized column names will be used as-is without prefixing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ColumnPrefix {
}
