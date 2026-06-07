package net.vortexdevelopment.vinject.database.cache;

import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Two-tier cache implementation with HOT and NORMAL tiers.
 * Frequently accessed entities are promoted to HOT tier for longer retention.
 */
public class TwoTierCache<K, V> implements Cache<K, V> {
    
    private static final int HOT_PROMOTION_THRESHOLD = 10; // Accesses needed for promotion
    private static final long HOT_PROMOTION_WINDOW_MS = 60_000; // 1 minute window
    
    private final int hotTierSize;
    private final int normalTierSize;
    private final Map<K, CacheEntry<V>> hotTier;
    private final Map<K, CacheEntry<V>> normalTier;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong promotions = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public TwoTierCache(int hotTierSize, int normalTierSize) {
        this.hotTierSize = hotTierSize;
        this.normalTierSize = normalTierSize;
        
        // HOT tier with access-order LRU
        this.hotTier = new LinkedHashMap<K, CacheEntry<V>>(hotTierSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                boolean shouldRemove = size() > TwoTierCache.this.hotTierSize;
                if (shouldRemove) {
                    evictions.incrementAndGet();
                    DebugLogger.log("Evicting from HOT tier: %s", eldest.getKey());
                    
                    if (eldest.getValue().isDirty()) {
                        DebugLogger.log("WARNING: Evicting dirty entry from HOT tier: %s", eldest.getKey());
                    }
                }
                return shouldRemove;
            }
        };
        
        // NORMAL tier with access-order LRU (more aggressive eviction)
        this.normalTier = new LinkedHashMap<K, CacheEntry<V>>(normalTierSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                boolean shouldRemove = size() > TwoTierCache.this.normalTierSize;
                if (shouldRemove) {
                    evictions.incrementAndGet();
                    DebugLogger.log("Evicting from NORMAL tier: %s", eldest.getKey());
                    
                    if (eldest.getValue().isDirty()) {
                        DebugLogger.log("WARNING: Evicting dirty entry from NORMAL tier: %s", eldest.getKey());
                    }
                }
                return shouldRemove;
            }
        };
        
        DebugLogger.log("Created Two-Tier cache: HOT=%d, NORMAL=%d", hotTierSize, normalTierSize);
    }
    
    @Override
    public V get(K key) {
        lock.writeLock().lock(); // Write lock because we may promote
        try {
            // Check HOT tier first
            CacheEntry<V> entry = hotTier.get(key);
            if (entry != null) {
                hits.incrementAndGet();
                entry.markAccessed();
                DebugLogger.log("Cache HIT in HOT tier for key: %s (access count: %d)", 
                        key, entry.getAccessCount().get());
                return entry.getValue();
            }
            
            // Check NORMAL tier
            entry = normalTier.get(key);
            if (entry != null) {
                hits.incrementAndGet();
                entry.markAccessed();
                
                // Check if entry should be promoted to HOT tier
                if (shouldPromote(entry)) {
                    promoteToHot(key, entry);
                } else {
                    DebugLogger.log("Cache HIT in NORMAL tier for key: %s (access count: %d)", 
                            key, entry.getAccessCount().get());
                }
                
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
            // New entries go to NORMAL tier by default
            CacheEntry<V> entry = new CacheEntry<>(value);
            
            // Remove from both tiers if exists
            hotTier.remove(key);
            normalTier.remove(key);
            
            normalTier.put(key, entry);
            DebugLogger.log("Added entry to NORMAL tier: %s (size: %d/%d)", 
                    key, normalTier.size(), normalTierSize);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void remove(K key) {
        lock.writeLock().lock();
        try {
            boolean removedFromHot = hotTier.remove(key) != null;
            boolean removedFromNormal = normalTier.remove(key) != null;
            
            if (removedFromHot || removedFromNormal) {
                DebugLogger.log("Removed entry for key: %s (from %s tier)", 
                        key, removedFromHot ? "HOT" : "NORMAL");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void invalidate() {
        lock.writeLock().lock();
        try {
            int totalSize = hotTier.size() + normalTier.size();
            DebugLogger.log("Invalidating all %d entries (HOT: %d, NORMAL: %d)", 
                    totalSize, hotTier.size(), normalTier.size());
            hotTier.clear();
            normalTier.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Collection<V> getAll() {
        lock.readLock().lock();
        try {
            List<V> all = new ArrayList<>();
            all.addAll(hotTier.values().stream()
                    .map(CacheEntry::getValue)
                    .collect(Collectors.toList()));
            all.addAll(normalTier.values().stream()
                    .map(CacheEntry::getValue)
                    .collect(Collectors.toList()));
            return all;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Map<K, CacheEntry<V>> getAllEntries() {
        lock.readLock().lock();
        try {
            Map<K, CacheEntry<V>> all = new ConcurrentHashMap<>();
            all.putAll(hotTier);
            all.putAll(normalTier);
            return all;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public Collection<CacheEntry<V>> getDirtyEntries() {
        lock.readLock().lock();
        try {
            List<CacheEntry<V>> dirty = new ArrayList<>();
            dirty.addAll(hotTier.values().stream()
                    .filter(CacheEntry::isDirty)
                    .collect(Collectors.toList()));
            dirty.addAll(normalTier.values().stream()
                    .filter(CacheEntry::isDirty)
                    .collect(Collectors.toList()));
            return dirty;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void markClean(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = hotTier.get(key);
            if (entry == null) {
                entry = normalTier.get(key);
            }
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
            CacheEntry<V> entry = hotTier.get(key);
            if (entry == null) {
                entry = normalTier.get(key);
            }
            if (entry != null) {
                entry.markDirty();
                DebugLogger.log("Marked entry as dirty: %s", key);
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
            return hotTier.size() + normalTier.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get number of promotions from NORMAL to HOT tier.
     */
    public long getPromotions() {
        return promotions.get();
    }
    
    /**
     * Check if an entry should be promoted to HOT tier.
     */
    private boolean shouldPromote(CacheEntry<V> entry) {
        long now = System.currentTimeMillis();
        long age = now - entry.getLastAccess();
        int accessCount = entry.getAccessCount().get();
        
        // Promote if accessed frequently within time window
        return accessCount >= HOT_PROMOTION_THRESHOLD && age < HOT_PROMOTION_WINDOW_MS;
    }
    
    /**
     * Promote an entry from NORMAL to HOT tier.
     */
    private void promoteToHot(K key, CacheEntry<V> entry) {
        normalTier.remove(key);
        hotTier.put(key, entry);
        promotions.incrementAndGet();
        
        DebugLogger.log("PROMOTED to HOT tier: %s (access count: %d, HOT size: %d/%d)", 
                key, entry.getAccessCount().get(), hotTier.size(), hotTierSize);
    }
}
