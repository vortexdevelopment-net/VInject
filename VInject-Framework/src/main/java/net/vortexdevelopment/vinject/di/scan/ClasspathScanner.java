package net.vortexdevelopment.vinject.di.scan;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.ArgumentResolver;
import net.vortexdevelopment.vinject.annotation.component.Registry;
import net.vortexdevelopment.vinject.annotation.component.Root;
import org.reflections.Configuration;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

        ConfigurationBuilder builder = new ConfigurationBuilder();
        if (!rootPackage.isEmpty()) {
            builder.forPackage(rootPackage);
        }
        for (String includedPackage : includedPackages) {
            builder.forPackage(includedPackage);
        }

        builder.filterInputsBy(s -> {
            if (s == null) {
                return false;
            }
            if (s.startsWith("META-INF")) {
                return false;
            }
            if (!s.endsWith(".class")) {
                return false;
            }

            boolean isUnderRoot = rootPackage.isEmpty() || isClassInPackagePath(s, rootPackagePath);
            boolean isUnderIncluded = false;
            for (String includedPackage : includedPackages) {
                String includedPath = includedPackage.replace('.', '/');
                if (isClassInPackagePath(s, includedPath)) {
                    isUnderIncluded = true;
                    break;
                }
            }

            if (!isUnderRoot && !isUnderIncluded) {
                return false;
            }

            // Check ignored packages
            for (String ignoredPackage : ignoredPackages) {
                String ignoredPath = ignoredPackage.replace('.', '/');
                if (!isUnderIncluded && isClassInPackagePath(s, ignoredPath)) {
                    return false;
                }
            }

            return true;
        });

        return builder;
    }

    private static boolean isClassInPackagePath(String classFilePath, String packagePath) {
        if (packagePath == null || packagePath.isEmpty()) {
            return true;
        }
        return classFilePath.startsWith(packagePath + "/") || classFilePath.equals(packagePath + ".class");
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
     * Find all subtypes of a specific class or interface.
     */
    public <T> Set<Class<? extends T>> getSubTypesOf(Class<T> type) {
        return reflections.getSubTypesOf(type);
    }

    /**
     * Scans for classes annotated with @Registry.
     */
    public Set<Class<?>> scanRegistryHandlers(Predicate<Class<?>> filter) {
        return scanAndFilter(Registry.class, filter);
    }

    /**
     * Scans for classes annotated with @ArgumentResolver.
     */
    public Set<Class<?>> scanArgumentResolvers(Predicate<Class<?>> filter) {
        return scanAndFilter(ArgumentResolver.class, filter);
    }

    /**
     * Universal method for scanning and filtering annotated classes.
     *
     * @param annotation The annotation to scan for
     * @param filter     Optional filter (e.g., canLoadClass check)
     * @return Filtered set of classes
     */
    public Set<Class<?>> scanAndFilter(Class<? extends Annotation> annotation, Predicate<Class<?>> filter) {
        return reflections.getTypesAnnotatedWith(annotation).stream()
                .filter(filter)
                .collect(Collectors.toSet());
    }
}
