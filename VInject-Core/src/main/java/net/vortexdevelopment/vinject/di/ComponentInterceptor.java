package net.vortexdevelopment.vinject.di;

/**
 * Interface for intercepting component registration in the VInject framework.
 * Any component implementing this interface will be automatically registered
 * and called whenever a new component is added to the container.
 */
public interface ComponentInterceptor {
    /**
     * Called when a component instance is created and registered in the container.
     * This is called AFTER the instance is created and cached, but BEFORE 
     * dependencies are injected into its fields.
     *
     * @param clazz     The class of the component
     * @param instance  The instance of the component
     * @param container The dependency container
     */
    void onComponentRegistered(Class<?> clazz, Object instance, DependencyContainer container);
}
