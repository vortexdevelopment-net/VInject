package net.vortexdevelopment.vinject.annotation.util;

import java.lang.annotation.*;

/**
 * Sets a system property when the annotated component is loaded.
 * Can be used multiple times via @SetSystemProperties.
 * 
 * Example:
 * <pre>
 * {@code @SetSystemProperty(name = "app.mode", value = "development")}
 * public class TestApp {
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(SetSystemProperties.class)
public @interface SetSystemProperty {
    /**
     * System property name.
     */
    String name();
    
    /**
     * System property value.
     */
    String value();
}
