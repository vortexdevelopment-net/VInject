package net.vortexdevelopment.vinject.di.scan;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.Registry;
import net.vortexdevelopment.vinject.annotation.Root;
import org.reflections.Configuration;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Encapsulates the logic for scanning the classpath for annotated classes.
 * Centralizes package filtering and Reflections configuration.
 */
@Getter
public class ClasspathScanner {

    /**
     * -- GETTER --
     *  Access the underlying Reflections instance if advanced scanning is needed.
     */
    private final Reflections reflections;

    public ClasspathScanner(Root rootAnnotation, Class<?> rootClass) {
        this.reflections = new Reflections(createConfiguration(rootAnnotation, rootClass));
    }

    /**
     * Helper to create Reflections configuration based on @Root settings.
     */
    public static Configuration createConfiguration(Root rootAnnotation, Class<?> rootClass) {
        String rootPackage = getEffectivePackageName(rootAnnotation, rootClass);
        String rootPackagePath = rootPackage.replace('.', '/');
        String[] ignoredPackages = rootAnnotation.ignoredPackages();
        String[] includedPackages = rootAnnotation.includedPackages();

        return new ConfigurationBuilder()
                .forPackage(rootPackage)
                .filterInputsBy(s -> {
                    if (s == null) return false;
                    if (s.startsWith("META-INF")) return false;
                    if (!s.endsWith(".class")) return false;

                    // Only include classes under the root package path
                    if (!s.startsWith(rootPackagePath + "/") && !s.equals(rootPackagePath + ".class")) {
                        return false;
                    }

                    // Check ignored packages
                    for (String ignoredPackage : ignoredPackages) {
                        String ignoredPath = ignoredPackage.replace('.', '/');
                        if (s.startsWith(ignoredPath)) {
                            return false;
                        }
                    }

                    // Check included packages (override ignored)
                    for (String includedPackage : includedPackages) {
                        String includedPath = includedPackage.replace('.', '/');
                        if (s.startsWith(includedPath)) {
                            return true;
                        }
                    }

                    return true;
                });
    }

    /**
     * Resolves the package name to scan, either from annotation or class.
     */
    public static String getEffectivePackageName(Root rootAnnotation, Class<?> rootClass) {
        String packageName = rootAnnotation.packageName();
        if (packageName == null || packageName.isEmpty()) {
            Package pkg = rootClass.getPackage();
            if (pkg != null) {
                packageName = pkg.getName();
            } else {
                String className = rootClass.getName();
                int lastDot = className.lastIndexOf('.');
                packageName = (lastDot > 0) ? className.substring(0, lastDot) : "";
            }
        }
        return packageName;
    }

    /**
     * Find all types annotated with a specific annotation.
     */
    public Set<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation) {
        return reflections.getTypesAnnotatedWith(annotation);
    }

    /**
     * Scans for classes annotated with @Registry.
     */
    public Set<Class<?>> scanRegistryHandlers() {
        return getTypesAnnotatedWith(Registry.class);
    }

    /**
     * Scans for classes annotated with @ArgumentResolver.
     */
    public Set<Class<?>> scanArgumentResolvers() {
        return getTypesAnnotatedWith(ArgumentResolver.class);
    }

    /**
     * Universal method for scanning and filtering annotated classes.
     *
     * @param annotation The annotation to scan for
     * @param filter     Optional filter (e.g., canLoadClass check)
     * @return Filtered set of classes
     */
    public Set<Class<?>> scanAndFilter(Class<? extends Annotation> annotation, Predicate<Class<?>> filter) {
        Set<Class<?>> types = getTypesAnnotatedWith(annotation);
        if (filter != null) {
            return types.stream().filter(filter).collect(Collectors.toSet());
        }
        return types;
    }
}
