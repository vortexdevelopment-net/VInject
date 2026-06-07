package net.vortexdevelopment.vinject.annotation.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables debug logging for specified classes.
 * Use this on your application root or test classes to enable debugging for other components.
 * 
 * Example:
 * <pre>
 * {@code @EnableDebugFor({UserRepository.class, CacheManager.class})}
 * public class TestApp {
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableDebugFor {
    /**
     * Classes to enable debug logging for.
     */
    Class<?>[] value();
}
