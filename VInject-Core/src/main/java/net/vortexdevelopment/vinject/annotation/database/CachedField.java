package net.vortexdevelopment.vinject.annotation.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that modify entity fields and should track the modification.
 * When a method is annotated with @Cached("fieldName"), the transformer will automatically
 * add code to track that the field has been modified by adding it to the modifiedFields set.
 * 
 * Example:
 * <pre>
 * {@code @Cached("amount")
 * public void addAmount(BigDecimal value) {
 *     this.amount = this.amount.add(value);
 * }}
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CachedField {
    /**
     * The name of the field that is being modified by this method.
     * 
     * @return the field name to track
     */
    String value();
}
