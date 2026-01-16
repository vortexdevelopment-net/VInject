package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * TTL-based cache implementation with time-based eviction.
 * Entries expire after a configured TTL duration.
 */
public class TTLCache<K, V> implements Cache<K, V> {
    
    private final long ttlMillis;
    private final Map<K, CacheEntry<V>> storage = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    
    public TTLCache(long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000;
        DebugLogger.log("Created TTL cache with TTL=%d seconds", ttlSeconds);
    }
    
    @Override
    public V get(K key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry != null) {
            if (entry.isExpired(ttlMillis)) {
                DebugLogger.log("Entry expired for key: %s (age: %d ms)", 
                        key, System.currentTimeMillis() - entry.getLastWrite());
                storage.remove(key);
                evictions.incrementAndGet();
                misses.incrementAndGet();
                return null;
            }
            hits.incrementAndGet();
            entry.markAccessed();
            DebugLogger.log("Cache HIT for key: %s", key);
            return entry.getValue();
        }
        misses.incrementAndGet();
        DebugLogger.log("Cache MISS for key: %s", key);
        return null;
    }
    
    @Override
    public void put(K key, V value) {
        DebugLogger.log("Caching entry for key: %s with TTL=%d seconds", key, ttlMillis / 1000);
        storage.put(key, new CacheEntry<>(value));
    }
    
    @Override
    public void remove(K key) {
        DebugLogger.log("Removing entry for key: %s", key);
        storage.remove(key);
    }
    
    @Override
    public void invalidate() {
        DebugLogger.log("Invalidating all %d entries", storage.size());
        storage.clear();
    }
    
    /**
     * Clean up expired entries.
     * Should be called periodically by the cache manager.
     */
    public void cleanup() {
        List<K> expiredKeys = new ArrayList<>();
        for (Map.Entry<K, CacheEntry<V>> entry : storage.entrySet()) {
            if (entry.getValue().isExpired(ttlMillis)) {
                expiredKeys.add(entry.getKey());
            }
        }
        
        if (!expiredKeys.isEmpty()) {
            DebugLogger.log("Cleaning up %d expired entries", expiredKeys.size());
            for (K key : expiredKeys) {
                storage.remove(key);
                evictions.incrementAndGet();
            }
        }
    }
    
    @Override
    public Collection<V> getAll() {
        return storage.values().stream()
                .filter(entry -> !entry.isExpired(ttlMillis))
                .map(CacheEntry::getValue)
                .collect(Collectors.toList());
    }
    
    @Override
    public Map<K, CacheEntry<V>> getAllEntries() {
        return new ConcurrentHashMap<>(storage);
    }
    
    @Override
    public Collection<CacheEntry<V>> getDirtyEntries() {
        return storage.values().stream()
                .filter(entry -> !entry.isExpired(ttlMillis))
                .filter(CacheEntry::isDirty)
                .collect(Collectors.toList());
    }
    
    @Override
    public void markClean(K key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry != null) {
            entry.markClean();
        }
    }
    
    @Override
    public void markDirty(K key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry != null) {
            entry.markDirty();
            DebugLogger.log("Marked entry as dirty for key: %s", key);
        }
    }
    
    @Override
    public long getHits() {
        return hits.get();
    }
    
    @Override
    public long getMisses() {
        return misses.get();
    }
    
    @Override
    public long getEvictions() {
        return evictions.get();
    }
    
    @Override
    public int size() {
        return storage.size();
    }
}
