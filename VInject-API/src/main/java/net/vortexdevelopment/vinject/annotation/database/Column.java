package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps an entity field to a database column.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    String name() default "";

    boolean nullable() default true;

    boolean unique() default false;

    boolean primaryKey() default false;

    boolean autoIncrement() default false;

    int length() default -1;

    int precision() default -1;

    int scale() default -1;
}
