package net.vortexdevelopment.vinject.database.cache;

import java.util.Collection;

/**
 * Interface for cache factory and management.
 * Responsible for creating cache instances and tracking global metrics.
 */
public interface CacheManager {
    
    /**
     * Create a cache with the specified configuration.
     * 
     * @param name unique name for the cache
     * @param config cache configuration
     * @return the created cache instance
     */
    <K, V> Cache<K, V> createCache(String name, CacheConfig config);
    
    /**
     * Destroy a cache and clean up resources.
     * 
     * @param name cache name
     */
    void destroyCache(String name);
    
    /**
     * Get all registered cache names.
     * 
     * @return collection of cache names
     */
    Collection<String> getCacheNames();
    
    /**
     * Get metrics for a specific cache.
     * 
     * @param name cache name
     * @return cache metrics, or null if cache doesn't exist
     */
    CacheMetrics getMetrics(String name);
    
    /**
     * Get aggregated metrics across all caches.
     * 
     * @return global cache metrics
     */
    CacheMetrics getGlobalMetrics();
    
    /**
     * Get a cache by name.
     * 
     * @param name cache name
     * @return the cache, or null if not found
     */
    <K, V> Cache<K, V> getCache(String name);
    
    /**
     * Register a custom cache instance.
     * 
     * @param name cache name
     * @param cache cache instance
     */
    void registerCache(String name, Cache<?, ?> cache);

    /**
     * Shutdown the cache manager and flush all caches.
     */
    void shutdown();
}
