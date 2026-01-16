package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache manager implementation.
 * Creates and manages cache instances based on configuration.
 */
public class CacheManagerImpl implements CacheManager {
    
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();
    
    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> createCache(String name, CacheConfig config) {
        if (caches.containsKey(name)) {
            DebugLogger.log("Cache already exists: %s, returning existing instance", name);
            return (Cache<K, V>) caches.get(name);
        }
        
        Cache<K, V> cache = createCacheInstance(config);
        caches.put(name, cache);
        
        DebugLogger.log("Created %s cache: %s (maxSize=%d, writeStrategy=%s)", 
                config.getPolicy().name(), name, config.getMaxSize(), 
                config.getWriteStrategy().name());
        
        return cache;
    }
    
    @Override
    public void destroyCache(String name) {
        Cache<?, ?> cache = caches.remove(name);
        if (cache != null) {
            DebugLogger.log("Destroying cache: %s", name);
            cache.invalidate();
        }
    }
    
    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }
    
    @Override
    public CacheMetrics getMetrics(String name) {
        Cache<?, ?> cache = caches.get(name);
        if (cache == null) {
            return null;
        }
        
        return buildMetrics(cache);
    }
    
    @Override
    public CacheMetrics getGlobalMetrics() {
        long totalHits = 0;
        long totalMisses = 0;
        long totalEvictions = 0;
        int totalSize = 0;
        
        for (Cache<?, ?> cache : caches.values()) {
            totalHits += cache.getHits();
            totalMisses += cache.getMisses();
            totalEvictions += cache.getEvictions();
            totalSize += cache.size();
        }
        
        return CacheMetrics.builder()
                .hits(totalHits)
                .misses(totalMisses)
                .evictions(totalEvictions)
                .size(totalSize)
                .hitRate(CacheMetrics.calculateHitRate(totalHits, totalMisses))
                .build();
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getCache(String name) {
        return (Cache<K, V>) caches.get(name);
    }
    
    @Override
    public void shutdown() {
        DebugLogger.log("Shutting down cache manager, flushing %d caches", caches.size());
        
        for (Map.Entry<String, Cache<?, ?>> entry : caches.entrySet()) {
            String name = entry.getKey();
            Cache<?, ?> cache = entry.getValue();
            
            // Log dirty entries before shutdown
            Collection<?> dirtyEntries = cache.getDirtyEntries();
            if (!dirtyEntries.isEmpty()) {
                DebugLogger.log("WARNING: Cache %s has %d dirty entries at shutdown", 
                        name, dirtyEntries.size());
            }
            
            cache.invalidate();
        }
        
        caches.clear();
    }
    
    /**
     * Create a cache instance based on policy.
     */
    private <K, V> Cache<K, V> createCacheInstance(CacheConfig config) {
        switch (config.getPolicy()) {
            case STATIC:
                return new StaticCache<>();
                
            case TTL:
                return new TTLCache<>(config.getTtlSeconds());
                
            case LRU:
                return new SimpleLRUCache<>(config.getMaxSize());
                
            case HOT_AWARE:
                return new TwoTierCache<>(config.getHotTierSize(), config.getMaxSize());
                
            default:
                DebugLogger.log("Unknown cache policy: %s, defaulting to LRU", config.getPolicy());
                return new SimpleLRUCache<>(config.getMaxSize());
        }
    }
    
    /**
     * Build metrics from a cache instance.
     */
    private CacheMetrics buildMetrics(Cache<?, ?> cache) {
        long hits = cache.getHits();
        long misses = cache.getMisses();
        
        return CacheMetrics.builder()
                .hits(hits)
                .misses(misses)
                .evictions(cache.getEvictions())
                .size(cache.size())
                .hitRate(CacheMetrics.calculateHitRate(hits, misses))
                .build();
    }
}
