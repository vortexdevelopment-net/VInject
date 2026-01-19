package net.vortexdevelopment.vinject.database.cache;

/**
 * Interface for components that contribute entities to be cached when a proactive
 * cache identifier is provided.
 */
public interface CacheContributor<T> {

    /**
     * @return the namespace this contributor handles (e.g. CacheNamespaces.PLAYER_UUID)
     */
    String getNamespace();

    /**
     * Contribute entities based on a provided identifier value.
     * 
     * @param value the identifier value (e.g. a UUID)
     * @param provider the provider to accept loaded entities
     */
    void contribute(Object value, CacheProvider<T> provider);

    /**
     * Invalidate entities based on a provided identifier value.
     * Default implementation does nothing if invalidation is not supported.
     * 
     * @param value the identifier value
     * @param provider the provider to invalidate entities
     */
    default void invalidate(Object value, CacheProvider<T> provider) {}
}
