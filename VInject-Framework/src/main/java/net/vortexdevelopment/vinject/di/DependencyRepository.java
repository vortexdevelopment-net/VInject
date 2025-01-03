package net.vortexdevelopment.vinject.di;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DependencyRepository {

    /**
     * Get a dependency from the repository
     * @param dependency The class of the dependency
     * @return The dependency instance or null if it doesn't exist
     * @param <T> The type of the dependency
     */
    @Nullable <T> T getDependency(Class<T> dependency);

    /**
     * Inject the dependencies into the object
     * <p>
     * Does not inject static dependencies, use {@link #injectStatic(Class)} for that
     * @param object The object to inject the dependencies into
     */
    void inject(@NotNull Object object);

    /**
     * Inject the static dependencies into the class
     * @param clazz The class to inject the dependencies into
     */
    void injectStatic(@NotNull Class<?> clazz);

    /**
     * Inject the static dependencies into the object
     * @param instance The object to inject the dependencies into
     */
    default void injectStatic(@NotNull Object instance) {
        injectStatic(instance.getClass());
    }

    /**
     * Add a dependency to the repository
     * @param clazz The class of the dependency
     * @param instance The instance of the dependency
     */
    void addBean(@NotNull Class<?> clazz, @NotNull Object instance);

    static @NotNull DependencyRepository getInstance() {
        return DependencyContainer.getInstance();
    }
}
