package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for classes that provide argument resolution for dependency injection.
 * Classes annotated with this must implement the ArgumentResolverProcessor interface.
 * 
 * <p>Similar to @Registry, but for argument/parameter resolution instead of component registration.
 * 
 * <p>Example:
 * <pre>
 * {@code @ArgumentResolver(value = MyCustomAnnotation.class, priority = 20)}
 * public class MyCustomResolver implements ArgumentResolverProcessor {
 *     // implementation
 * }
 * </pre>
 * 
 * <p>To handle multiple annotations:
 * <pre>
 * {@code @ArgumentResolver(values = {Annotation1.class, Annotation2.class}, priority = 20)}
 * public class MultiAnnotationResolver implements ArgumentResolverProcessor {
 *     // implementation
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ArgumentResolver {
    
    /**
     * A single annotation type this resolver handles.
     * Ignored if {@link #values()} is provided and non-empty.
     * 
     * @return The annotation class
     */
    Class<? extends Annotation> value();
    
    /**
     * Multiple annotation types this resolver handles.
     * If provided and non-empty, {@link #value()} is ignored.
     * 
     * @return Array of annotation classes
     */
    Class<? extends Annotation>[] values() default {};
    
    /**
     * Priority order for resolution (higher values are checked first).
     * Built-in resolvers (@Value, @Inject) have priorities 100 and 50 respectively.
     * 
     * @return The priority value (default: 10)
     */
    int priority() default 10;
}

