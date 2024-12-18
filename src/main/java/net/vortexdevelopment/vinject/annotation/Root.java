package net.vortexdevelopment.vinject.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Annotation to mark the main class of the plugin
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Root {

    /**
     * The root package name to scan
     * @return root package name
     */
    String packageName();

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
}
