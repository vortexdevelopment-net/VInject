package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Static cache implementation that never evicts entries.
 * Best for small, static datasets that fit in memory.
 */
public class StaticCache<K, V> implements Cache<K, V> {
    
    private final Map<K, CacheEntry<V>> storage = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    
    @Override
    public V get(K key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry != null) {
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
        DebugLogger.log("Caching entry for key: %s", key);
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
    
    @Override
    public Collection<V> getAll() {
        return storage.values().stream()
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
        return 0; // Static cache never evicts
    }
    
    @Override
    public int size() {
        return storage.size();
    }
}
