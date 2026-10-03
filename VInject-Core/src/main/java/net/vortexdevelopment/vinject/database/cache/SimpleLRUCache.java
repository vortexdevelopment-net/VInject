package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Simple LRU cache implementation using LinkedHashMap with access-order.
 * Evicts least recently used entries when max size is exceeded.
 */
public class SimpleLRUCache<K, V> implements Cache<K, V> {
    
    private final int maxSize;
    private final Map<K, CacheEntry<V>> storage;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public SimpleLRUCache(int maxSize) {
        this.maxSize = maxSize;
        // LinkedHashMap with access-order. Eviction is performed explicitly so
        // pinned and dirty entries can be skipped instead of being discarded.
        this.storage = new LinkedHashMap<>(maxSize, 0.75f, true);
        DebugLogger.log("Created LRU cache with maxSize=%d", maxSize);
    }
    
    @Override
    public V get(K key) {
        // LinkedHashMap access-order updates the map during get().
        lock.writeLock().lock();
        try {
            CacheEntry<V> entry = storage.get(key);
            if (entry != null) {
                hits.incrementAndGet();
                entry.markAccessed();
                DebugLogger.log("Cache HIT for key: %s (access count: %d)", 
                        key, entry.getAccessCount().get());
                return entry.getValue();
            }
            misses.incrementAndGet();
            DebugLogger.log("Cache MISS for key: %s", key);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            DebugLogger.log("Caching entry for key: %s (current size: %d/%d)", 
                    key, storage.size(), maxSize);
            CacheEntry<V> existing = storage.get(key);
            if (existing != null) {
                existing.replaceValue(value);
            } else {
                storage.put(key, new CacheEntry<>(value));
            }
            evictIfNeeded();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evictIfNeeded() {
        while (storage.size() > maxSize) {
            Map.Entry<K, CacheEntry<V>> candidate = storage.entrySet().stream()
                    .filter(entry -> !entry.getValue().isPinned())
                    .filter(entry -> !entry.getValue().isDirty())
                    .findFirst()
                    .orElse(null);
            if (candidate == null) {
                return;
            }

            storage.remove(candidate.getKey());
            evictions.incrementAndGet();
            DebugLogger.log("Evicting entry for key: %s (LRU)", candidate.getKey());
        }
    }
    
    @Override
    public void remove(K key) {
        lock.writeLock().lock();
        try {
            DebugLogger.log("Removing entry for key: %s", key);
            storage.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void invalidate() {
        lock.writeLock().lock();
        try {
            DebugLogger.log("Invalidating all %d entries", storage.size());
            storage.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Collection<V> getAll() {
        lock.readLock().lock();
        try {
            return storage.values().stream()
                    .map(CacheEntry::getValue)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Map<K, CacheEntry<V>> getAllEntries() {
        lock.readLock().lock();
        try {
            return new LinkedHashMap<>(storage);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Collection<CacheEntry<V>> getDirtyEntries() {
        lock.readLock().lock();
        try {
            return storage.values().stream()
                    .filter(CacheEntry::isDirty)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void markClean(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = storage.get(key);
            if (entry != null) {
                entry.markClean();
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void markDirty(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = storage.get(key);
            if (entry != null) {
                entry.markDirty();
                DebugLogger.log("Marked entry as dirty for key: %s", key);
            }
        } finally {
            lock.readLock().unlock();
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
        lock.readLock().lock();
        try {
            return storage.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
