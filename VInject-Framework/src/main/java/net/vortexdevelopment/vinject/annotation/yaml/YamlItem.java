package net.vortexdevelopment.vinject.annotation.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a compact YAML item/data object.
 * 
 * When a class is annotated with @YamlItem, its fields will be serialized
 * without blank lines between them. This is useful for data transfer objects
 * or value objects that should be displayed compactly in YAML.
 * 
 * Example:
 * 
 * <pre>
 * @YamlItem
 * public class ItemStack {
 *     private String itemType;
 *     private int quantity;
 * }
 * </pre>
 * 
 * Will be serialized as:
 * 
 * <pre>
 * ItemStack:
 *   itemType: stone
 *   quantity: 64
 * </pre>
 * 
 * Without @YamlItem, configuration fields would have blank lines between them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface YamlItem {
}
