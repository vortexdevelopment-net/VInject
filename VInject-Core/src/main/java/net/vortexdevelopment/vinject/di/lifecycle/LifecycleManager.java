package net.vortexdevelopment.vinject.di.lifecycle;

import net.vortexdevelopment.vinject.annotation.lifecycle.OnDestroy;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnLoad;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.annotation.util.EnableDebug;
import net.vortexdevelopment.vinject.annotation.util.EnableDebugFor;
import net.vortexdevelopment.vinject.annotation.util.SetSystemProperty;
import net.vortexdevelopment.vinject.annotation.util.SetSystemProperties;
import net.vortexdevelopment.vinject.debug.DebugLogger;
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
        
        // Process utility annotations
        processDebugAnnotations(clazz);
        processSystemPropertyAnnotations(clazz);


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
        
        // Process debug annotations
        processDebugAnnotations(clazz);
        
        // Process system property annotations
        processSystemPropertyAnnotations(clazz);
        
        // Invoke @OnLoad methods
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
    
    /**
     * Process @EnableDebug and @EnableDebugFor annotations.
     */
    public void processDebugAnnotations(Class<?> clazz) {
        // Check for @EnableDebug (enable debug for this class)
        if (clazz.isAnnotationPresent(EnableDebug.class)) {
            DebugLogger.enableDebugFor(clazz);
        }
        
        // Check for @EnableDebugFor (enable debug for other classes)
        if (clazz.isAnnotationPresent(EnableDebugFor.class)) {
            EnableDebugFor annotation = clazz.getAnnotation(EnableDebugFor.class);
            DebugLogger.enableDebugFor(annotation.value());
        }
    }
    
    /**
     * Process @SetSystemProperty annotations.
     */
    public void processSystemPropertyAnnotations(Class<?> clazz) {
        // Check for single @SetSystemProperty
        if (clazz.isAnnotationPresent(SetSystemProperty.class)) {
            SetSystemProperty annotation = clazz.getAnnotation(SetSystemProperty.class);
            System.setProperty(annotation.name(), annotation.value());
        }
        
        // Check for multiple @SetSystemProperties
        if (clazz.isAnnotationPresent(SetSystemProperties.class)) {
            SetSystemProperties annotation = clazz.getAnnotation(SetSystemProperties.class);
            for (SetSystemProperty property : annotation.value()) {
                System.setProperty(property.name(), property.value());
            }
        }
    }
}
