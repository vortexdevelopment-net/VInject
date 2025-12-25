package net.vortexdevelopment.vinject.di.lifecycle;

import net.vortexdevelopment.vinject.annotation.OnDestroy;
import net.vortexdevelopment.vinject.annotation.OnLoad;
import net.vortexdevelopment.vinject.annotation.PostConstruct;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the lifecycle of components in the VInject framework.
 * Handles @PostConstruct, @OnLoad, and @OnDestroy annotations.
 */
public class LifecycleManager {

    private final DependencyContainer container;
    private final List<Method> destroyMethods = new ArrayList<>();

    public LifecycleManager(DependencyContainer container) {
        this.container = container;
    }

    /**
     * Invokes all methods annotated with @PostConstruct on the given instance.
     * Methods are invoked in descending order of priority.
     *
     * @param instance The instance to process
     */
    public void invokePostConstruct(Object instance) {
        Class<?> clazz = instance.getClass();
        Method[] methods = clazz.getDeclaredMethods();


        for (Method method : methods) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                invokeLifecycleMethod(instance, method);
            }
        }
    }

    /**
     * Invokes all methods annotated with @OnLoad on the given instance.
     *
     * @param instance The instance to process
     */
    public void invokeOnLoad(Object instance) {
        Class<?> clazz = instance.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(OnLoad.class)) {
                invokeLifecycleMethod(instance, method);
            }
        }
    }

    /**
     * Helper to invoke a lifecycle method with parameter resolution.
     */
    private void invokeLifecycleMethod(Object instance, Method method) {
        try {
            method.setAccessible(true);
            if (method.getParameterCount() == 0) {
                method.invoke(instance);
            } else {
                Parameter[] parameters = method.getParameters();
                Object[] args = new Object[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    // Delegate argument resolution back to the container for now
                    args[i] = container.resolveLifecycleArgument(instance, method, parameters[i], i);
                }
                method.invoke(instance, args);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error invoking lifecycle method: " + method.getName() + 
                                       " on " + instance.getClass().getName(), e);
        }
    }

    /**
     * Scans for @OnDestroy methods in all registered dependencies.
     */
    public synchronized void scanDestroyMethods() {
        destroyMethods.clear();
        for (Object instance : container.getDependencies().values()) {
            if (instance != null) {
                scanDestroyMethodsForClassOnly(instance.getClass());
            }
        }
    }

    /**
     * Scans a specific class for @OnDestroy methods and adds them to the registry.
     *
     * @param clazz The class to scan
     */
    public synchronized void scanDestroyMethodsForClassOnly(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(OnDestroy.class)) {
                if (!destroyMethods.contains(method)) {
                    destroyMethods.add(method);
                }
            }
        }
    }

    /**
     * Invokes all registered @OnDestroy methods in descending order of priority.
     */
    public void invokeDestroyMethods() {

        for (Method method : destroyMethods) {
            try {
                Object instance = container.getDependencyOrNull(method.getDeclaringClass());
                if (instance != null) {
                    method.setAccessible(true);
                    method.invoke(instance);
                }
            } catch (Exception e) {
                System.err.println("Error invoking @OnDestroy method: " + method.getName() + 
                                   " in class: " + method.getDeclaringClass().getName());
                e.printStackTrace();
            }
        }
    }

    /**
     * Clears all registered lifecycle metadata.
     */
    public void clear() {
        destroyMethods.clear();
    }
}
