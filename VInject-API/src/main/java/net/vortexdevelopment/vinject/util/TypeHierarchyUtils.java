package net.vortexdevelopment.vinject.util;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects super-types that a component or bean should be registered under for injection.
 */
public final class TypeHierarchyUtils {

    private static final Set<String> EXCLUDED_TYPES = Set.of(
            "java.lang.Object",
            "java.io.Serializable",
            "java.lang.Cloneable",
            "java.lang.AutoCloseable"
    );

    private TypeHierarchyUtils() {
    }

    public static Set<Class<?>> collectRegistrationTypes(Class<?> implementationClass) {
        Set<Class<?>> types = new LinkedHashSet<>();
        Class<?> current = implementationClass;
        while (current != null && current != Object.class) {
            for (Class<?> iface : current.getInterfaces()) {
                collectInterfaceHierarchy(iface, types);
            }
            current = current.getSuperclass();
        }

        Class<?> walk = implementationClass;
        while (walk != null && walk != Object.class) {
            if (!isExcluded(walk)) {
                types.add(walk);
            }
            walk = walk.getSuperclass();
        }

        types.remove(implementationClass);
        types.removeIf(TypeHierarchyUtils::isExcluded);
        return types;
    }

    private static void collectInterfaceHierarchy(Class<?> iface, Set<Class<?>> types) {
        if (iface == null || isExcluded(iface)) {
            return;
        }
        types.add(iface);
        for (Class<?> parent : iface.getInterfaces()) {
            collectInterfaceHierarchy(parent, types);
        }
    }

    private static boolean isExcluded(Class<?> type) {
        return EXCLUDED_TYPES.contains(type.getName());
    }
}
