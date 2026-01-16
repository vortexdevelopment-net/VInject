package net.vortexdevelopment.vinject.database.cache;

/**
 * Enum defining cache behavior policies.
 */
public enum CachePolicy {
    /**
     * Preload all entities on initialization, never evict.
     * Best for small, static datasets.
     */
    STATIC,
    
    /**
     * Time-based eviction using TTL (Time To Live).
     * Entries expire after a configured duration.
     */
    TTL,
    
    /**
     * Least Recently Used eviction based on size and access patterns.
     * Single-tier cache with LRU eviction when max size is exceeded.
     */
    LRU,
    
    /**
     * Two-tier cache with hot entity promotion.
     * HOT tier for frequently accessed entities, NORMAL tier with aggressive eviction.
     */
    HOT_AWARE
}
