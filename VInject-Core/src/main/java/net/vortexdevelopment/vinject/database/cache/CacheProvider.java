package net.vortexdevelopment.vinject.database.cache;

/**
 * Functional interface for providing entities to a cache contributor.
 */
@FunctionalInterface
public interface CacheProvider<T> {
    /**
     * Accept an entity to be placed into the managed cache.
     * @param entity the entity to cache
     */
    void accept(T entity);
}
