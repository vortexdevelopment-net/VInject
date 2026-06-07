package net.vortexdevelopment.vinject.event;

import net.vortexdevelopment.vinject.annotation.lifecycle.OnEvent;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages event registration and dispatching for the VInject framework.
 * Allows components to listen for specific events and receive injected dependencies.
 */
public class EventManager {
    
    private final Map<String, List<Method>> eventListeners = new ConcurrentHashMap<>();
    private final DependencyContainer container;

    public EventManager(DependencyContainer container) {
        this.container = container;
    }

    /**
     * Emits an event, invoking all registered listener methods.
     * Method parameters are automatically resolved from the dependency container.
     *
     * @param eventName The name of the event to emit
     */
    public void emitEvent(@NotNull String eventName) {
        List<Method> listeners = eventListeners.get(eventName);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        for (Method method : listeners) {
            try {
                Object instance = container.getDependencyOrNull(method.getDeclaringClass());
                if (instance == null) {
                    continue;
                }

                method.setAccessible(true);

                if (method.getParameterCount() == 0) {
                    method.invoke(instance);
                } else {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    Object[] parameters = new Object[parameterTypes.length];
                    for (int i = 0; i < parameterTypes.length; i++) {
                        Object dependency = container.getDependencyOrNull(parameterTypes[i]);
                        if (dependency == null) {
                            System.err.println("Dependency not found for event listener: " + 
                                               method.getName() + " with parameter: " + parameterTypes[i].getName());
                            continue;
                        }
                        parameters[i] = dependency;
                    }
                    method.invoke(instance, parameters);
                }
            } catch (Exception e) {
                System.err.println("Error invoking event listener: " + method.getName() + " for event: " + eventName);
                e.printStackTrace();
            }
        }
    }

    /**
     * Scans a class for methods annotated with @OnEvent and registers them.
     *
     * @param clazz The class to scan for listeners
     */
    public void registerEventListeners(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(OnEvent.class)) {
                OnEvent onEvent = method.getAnnotation(OnEvent.class);
                for (String event : onEvent.value()) {
                    if (event != null && !event.isEmpty()) {
                        eventListeners.computeIfAbsent(event, k -> new ArrayList<>()).add(method);
                    }
                }
            }
        }
    }

    /**
     * Clears all registered event listeners.
     */
    public void clear() {
        eventListeners.clear();
    }
}
