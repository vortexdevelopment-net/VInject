package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for injecting property values from application.properties, environment variables, or system properties.
 * Supports Spring Boot-style property resolution with default values.
 * 
 * <p>Usage examples:
 * <pre>
 * {@code
 * // Inject a property value
 * @Value("${app.name}")
 * private String appName;
 * 
 * // Inject with default value
 * @Value("${app.timeout:5000}")
 * private int timeout;
 * 
 * // Constructor parameter injection
 * public MyService(@Value("${app.name}") String appName) {
 *     this.appName = appName;
 * }
 * }
 * </pre>
 * 
 * <p>Property resolution priority (highest to lowest):
 * <ol>
 *   <li>Environment variables</li>
 *   <li>System properties</li>
 *   <li>application.properties file</li>
 * </ol>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface Value {
    /**
     * The property key or expression to resolve.
     * Supports Spring Boot-style syntax: ${property.key:defaultValue}
     * 
     * @return The property expression
     */
    String value();
}

