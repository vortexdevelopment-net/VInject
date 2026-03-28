package net.vortexdevelopment.vinject.di.context;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Provides a highly efficient mechanism for passing 
 * runtime context parameters into the dependency injection container.
 * <p>
 * Instead of relying on singletons, specific method argument resolutions can 
 * draw beans directly from this context.
 */
public class InjectionContext {

    private static final ThreadLocal<Map<Class<?>, Object>> CONTEXT = new ThreadLocal<>();

    /**
     * Retrieves an instance bound to the current context scope.
     * 
     * @param type The class type to resolve.
     * @param <T> The expected generic type.
     * @return The instance if found in the current scoped context, otherwise null.
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        Map<Class<?>, Object> contextBeans = CONTEXT.get();
        if (contextBeans != null) {
            Object instance = contextBeans.get(type);
            if (instance != null) {
                return (T) instance;
            }
        }
        return null;
    }

    /**
     * Executes a runnable with the specified context beans bound in the current thread scope.
     * 
     * @param contextBeans The beans to temporarily register in this scope.
     * @param action The execution code.
     * @param <T> The return type.
     * @return The result of the action.
     * @throws Exception If the action throws an exception.
     */
    public static <T> T runWithContext(Map<Class<?>, Object> contextBeans, Callable<T> action) throws Exception {
        Map<Class<?>, Object> previous = CONTEXT.get();
        try {
            CONTEXT.set(Collections.unmodifiableMap(contextBeans));
            return action.call();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
    
    /**
     * Executes a runnable with the specified context beans bound in the current thread scope.
     * 
     * @param contextBeans The beans to temporarily register in this scope.
     * @param action The execution code.
     */
    public static void runWithContext(Map<Class<?>, Object> contextBeans, Runnable action) {
        Map<Class<?>, Object> previous = CONTEXT.get();
        try {
            CONTEXT.set(Collections.unmodifiableMap(contextBeans));
            action.run();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}
