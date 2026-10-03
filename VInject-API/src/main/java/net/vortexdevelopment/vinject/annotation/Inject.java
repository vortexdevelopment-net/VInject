package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields, methods, or parameters that need dependency injection.
 * 
 * <p>Supported targets:
 * <ul>
 *   <li>FIELD - Inject dependencies directly into fields</li>
 *   <li>METHOD - Inject dependencies via setter methods (recommended for circular dependencies)</li>
 *   <li>PARAMETER - Inject dependencies into constructor or method parameters</li>
 * </ul>
 * 
 * <p>Examples:
 * <pre>
 * // Field injection
 * {@literal @}Inject
 * private UserService userService;
 * 
 * // Setter injection (recommended for circular dependencies)
 * {@literal @}Inject
 * public void setUserService(UserService userService) {
 *     this.userService = userService;
 * }
 * 
 * // Constructor parameter injection
 * public MyService({@literal @}Inject UserService userService) {
 *     this.userService = userService;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Inject {
}
