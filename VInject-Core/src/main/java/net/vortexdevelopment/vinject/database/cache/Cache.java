package net.vortexdevelopment.vinject.database.cache;

import java.util.Collection;
import java.util.Map;

/**
 * Core cache interface for storing and retrieving cached entities.
 * 
 * @param <K> the key type
 * @param <V> the value type
 */
public interface Cache<K, V> {
    
    /**
     * Get a value from the cache.
     * 
     * @param key the key
     * @return the value, or null if not found
     */
    V get(K key);
    
    /**
     * Put a value into the cache.
     * 
     * @param key the key
     * @param value the value
     */
    void put(K key, V value);
    
    /**
     * Remove a value from the cache.
     * 
     * @param key the key
     */
    void remove(K key);
    
    /**
     * Invalidate (clear) the entire cache.
     */
    void invalidate();
    
    /**
     * Get all values in the cache.
     * 
     * @return collection of all cached values
     */
    Collection<V> getAll();
    
    /**
     * Get all cache entries (with metadata).
     * 
     * @return map of all cache entries
     */
    Map<K, CacheEntry<V>> getAllEntries();
    
    /**
     * Get all dirty entries (for write-back strategy).
     * 
     * @return collection of dirty cache entries
     */
    Collection<CacheEntry<V>> getDirtyEntries();
    
    /**
     * Mark an entry as clean (persisted).
     * 
     * @param key the key
     */
    void markClean(K key);
    
    /**
     * Mark an entry as dirty (modified).
     * 
     * @param key the key
     */
    void markDirty(K key);
    
    /**
     * Get cache hit count.
     */
    long getHits();
    
    /**
     * Get cache miss count.
     */
    long getMisses();
    
    /**
     * Get eviction count.
     */
    long getEvictions();
    
    /**
     * Get current cache size.
     */
    int size();
}
