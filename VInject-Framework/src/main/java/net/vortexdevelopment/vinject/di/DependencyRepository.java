package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.di.engine.InjectionEngine;
import net.vortexdevelopment.vinject.di.lifecycle.LifecycleManager;
import net.vortexdevelopment.vinject.event.EventManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DependencyRepository {

    /**
     * Get a dependency from the repository
     * @param dependency The class of the dependency
     * @return The dependency instance or null if it doesn't exist
     * @param <T> The type of the dependency
     * @throws NullPointerException if the dependency is not found
     */
    @NotNull <T> T getDependency(Class<T> dependency);

    /**
     * Get a dependency from the repository
     * @param dependency The class of the dependency
     * @return The dependency instance or null if it doesn't exist
     * @param <T> The type of the dependency
     */
    @Nullable <T> T getDependencyOrNull(Class<T> dependency);

    /**
     * Add a dependency to the repository
     * @param clazz The class of the dependency
     * @param instance The instance of the dependency
     */
    void addBean(@NotNull Class<?> clazz, @NotNull Object instance);

    /**
     * Get the injection engine
     * @return The injection engine
     */
    @NotNull
    InjectionEngine getInjectionEngine();

    /**
     * Get the event manager
     * @return The event manager
     */
    @NotNull
    EventManager getEventManager();

    /**
     * Get the lifecycle manager
     * @return The lifecycle manager
     */
    @NotNull
    LifecycleManager getLifecycleManager();

    static @NotNull DependencyRepository getInstance() {
        return DependencyContainer.getInstance();
    }
}
