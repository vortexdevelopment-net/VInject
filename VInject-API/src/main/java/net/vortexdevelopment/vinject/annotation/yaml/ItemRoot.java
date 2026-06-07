package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the YAML subtree that backs the current mapped object to a {@link net.vortexdevelopment.vinject.config.ConfigurationSection}
 * field.
 * <p>
 * Intended for {@link YamlItem} classes (especially batch-loaded entries with {@link YamlId}): the field receives the same
 * live section used when mapping the item, so you can read or mutate keys directly and persist them on save.
 * </p>
 * <p>
 * Use at most one {@code @ItemRoot} per class. The field type must be assignable to {@code ConfigurationSection}.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ItemRoot {
}
