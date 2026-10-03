package net.vortexdevelopment.vinject.annotation.database;

import net.vortexdevelopment.vinject.database.TemporalType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a field as a temporal field.
 * This annotation is used by the database manager to determine if a field is a temporal field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Temporal {

    String name() default "";

    TemporalType value() default TemporalType.TIMESTAMP;

    /**
     * If the field is nullable.
     * @return true if the field is nullable, false otherwise.
     */
    boolean nullable() default true;

    /**
     * If current_timestamp() should be used when the record is inserted.
     * @return true if current_timestamp() should be used when the record is inserted, false otherwise.
     */
    boolean currentTimestampOnInsert() default true;

    /**
     * If current_timestamp() should be used when the record is updated.
     * @return true if current_timestamp() should be used when the record is updated, false otherwise.
     */
    boolean currentTimestampOnUpdate() default false;

    /**
     * If the field is a null default.
     * If false, it will be CURRENT_TIMESTAMP.
     * @return true if the field is a null default, false otherwise.
     */
    boolean nullDefault() default false;

}
