package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;

/**
 * Interface for providing custom cache implementations or logic.
 */
public interface CacheResolver {

    /**
     * Resolve a cache instance for the given repository context.
     * 
     * @param context the repository invocation context
     * @param config the cache configuration
     * @param <K> the key type
     * @param <V> the entity type
     * @return a cache instance, must not be null
     */
    <K, V> Cache<K, V> resolve(RepositoryInvocationContext<?, ?> context, CacheConfig config);
}
