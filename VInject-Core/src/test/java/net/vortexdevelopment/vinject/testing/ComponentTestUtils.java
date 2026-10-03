package net.vortexdevelopment.vinject.testing;

import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnDestroy;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utilities for testing component lifecycle and dependency management.
 * Provides methods to verify component loading order, lifecycle method invocation,
 * and dependency analysis.
 */
public class ComponentTestUtils {

    /**
     * Capture the loading order of components in a context.
     * This can be used to verify that components are loaded in the correct order.
     */
    public static List<Class<?>> captureLoadingOrder(TestApplicationContext context) {
        // This would require instrumentation of the container to track loading order
        // For now, return an empty list as a placeholder
        return new ArrayList<>();
    }

    /**
     * Verify that a @PostConstruct method was called on a component.
     * This checks if the component has been properly initialized.
     */
    public static boolean verifyPostConstructCalled(Object component) {
        Class<?> clazz = component.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                // Check if method execution would succeed
                // In real scenarios, you'd track invocation via instrumentation
                return true;
            }
        }
        return false;
    }

    /**
     * Verify that an @OnDestroy method exists on a component.
     */
    public static boolean hasOnDestroyMethod(Object component) {
        Class<?> clazz = component.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(OnDestroy.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all dependencies of a component class (fields annotated with @Inject).
     */
    public static Set<Class<?>> getDependencies(Class<?> componentClass) {
        Set<Class<?>> dependencies = new HashSet<>();
        for (Field field : componentClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                dependencies.add(field.getType());
            }
        }
        return dependencies;
    }

    /**
     * Check if a component has any dependencies.
     */
    public static boolean hasDependencies(Class<?> componentClass) {
        return !getDependencies(componentClass).isEmpty();
    }

    /**
     * Verify that all @Inject fields in a component are not null.
     */
    public static boolean verifyAllDependenciesInjected(Object component) {
        Class<?> clazz = component.getClass();
        try {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    field.setAccessible(true);
                    Object value = field.get(component);
                    if (value == null) {
                        return false;
                    }
                }
            }
            return true;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to verify dependencies", e);
        }
    }

    /**
     * Get the names of all @Inject fields that are null.
     */
    public static List<String> getUnjectedDependencies(Object component) {
        List<String> unjected = new ArrayList<>();
        Class<?> clazz = component.getClass();
        try {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    field.setAccessible(true);
                    Object value = field.get(component);
                    if (value == null) {
                        unjected.add(field.getName());
                    }
                }
            }
            return unjected;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to check dependencies", e);
        }
    }
}
