package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Annotation to mark the main class of the plugin
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Root {

    /**
     * The root package name to scan.
     * If not specified (empty string), the package name will be automatically detected from the root class.
     * @return root package name, or empty string to auto-detect from root class
     */
    String packageName() default "";

    /**
     * Should a new instance created of the root class?
     * <p>It requires a default constructor to be present
     * @return true if a new instance should be created
     */
    boolean createInstance() default true;

    /**
     * Automatically load application.properties from the working directory
     * @return true if properties should be loaded
     */
    boolean loadProperties() default true;

    /**
     * Packages to ignore when scanning for classes in the root package
     * @return ignored packages
     */
    String[] ignoredPackages() default {};

    /**
     * Packages to include when scanning for classes
     * If you want to include sub packages from a package that is ignored
     * Example: Ignored package: org.example.libs is excluded, but you want to include org.example.libs.mylib
     * @return included packages
     */
    String[] includedPackages() default {};

    /**
     * Used for the Intellij VInject Framework plugin to mark custom annotations as components
     * <p>Used when a @Registry is used in a dependency project which will be available at runtime</p>
     * @return The custom annotations to mark as components
     */
    Class<? extends Annotation>[] componentAnnotations() default {};

    /**
     * Used for the Intellij VInject Framework plugin to register<br>
     * custom template paths from dependency projects for component creation.
     * <p>Example: If you have a custom component in a dependency with a component file
     * template you can register that artifact here</p>
     * @return The template artifacts to look for templates
     */
    TemplateDependency[] templateDependencies() default {};
}
