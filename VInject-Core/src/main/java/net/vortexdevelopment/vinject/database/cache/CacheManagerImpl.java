package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Cache manager implementation.
 * Creates and manages cache instances based on configuration.
 */
public class CacheManagerImpl implements CacheManager {
    
    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> maintenanceTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vinject-cache-flush");
        thread.setDaemon(true);
        return thread;
    });
    
    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> createCache(String name, CacheConfig config) {
        if (caches.containsKey(name)) {
            DebugLogger.log("Cache already exists: %s, returning existing instance", name);
            return (Cache<K, V>) caches.get(name);
        }
        
        Cache<K, V> cache = createCacheInstance(config);
        caches.put(name, cache);
        ScheduledFuture<?> maintenanceTask = scheduler.scheduleAtFixedRate(
                cache::cleanup,
                1,
                1,
                TimeUnit.SECONDS
        );
        maintenanceTasks.put(name, maintenanceTask);
        
        DebugLogger.log("Created %s cache: %s (maxSize=%d, writeStrategy=%s)", 
                config.getPolicy().name(), name, config.getMaxSize(), 
                config.getWriteStrategy().name());
        
        return cache;
    }
    
    @Override
    public void destroyCache(String name) {
        unregisterFlushTask(name);
        ScheduledFuture<?> maintenanceTask = maintenanceTasks.remove(name);
        if (maintenanceTask != null) {
            maintenanceTask.cancel(false);
        }
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
    public void registerCache(String name, Cache<?, ?> cache) {
        caches.put(name, cache);
        DebugLogger.log("Registered custom cache: %s", name);
    }

    @Override
    public void registerFlushTask(String name, Runnable action, int intervalSeconds) {
        if (intervalSeconds <= 0) {
            return;
        }
        unregisterFlushTask(name);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                action,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        flushTasks.put(name, task);
    }

    @Override
    public void unregisterFlushTask(String name) {
        ScheduledFuture<?> task = flushTasks.remove(name);
        if (task != null) {
            task.cancel(false);
        }
    }
    
    @Override
    public void shutdown() {
        DebugLogger.log("Shutting down cache manager, flushing %d caches", caches.size());
        
        flushTasks.values().forEach(task -> task.cancel(false));
        flushTasks.clear();
        maintenanceTasks.values().forEach(task -> task.cancel(false));
        maintenanceTasks.clear();
        scheduler.shutdownNow();

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
