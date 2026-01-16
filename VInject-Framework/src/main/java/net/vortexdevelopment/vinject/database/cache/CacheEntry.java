package net.vortexdevelopment.vinject.database.cache;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper for cached entities with metadata.
 * Tracks access patterns and dirty state for cache eviction and write-back strategies.
 */
@Getter
public class CacheEntry<T> {
    private final T value;
    private volatile long lastAccess;
    private volatile long lastWrite;
    private final AtomicInteger accessCount;
    private volatile boolean dirty;
    
    public CacheEntry(T value) {
        this.value = value;
        this.lastAccess = System.currentTimeMillis();
        this.lastWrite = System.currentTimeMillis();
        this.accessCount = new AtomicInteger(0);
        this.dirty = false;
    }
    
    /**
     * Mark this entry as accessed, updating timestamp and incrementing counter.
     */
    public void markAccessed() {
        this.lastAccess = System.currentTimeMillis();
        this.accessCount.incrementAndGet();
    }
    
    /**
     * Mark this entry as dirty (modified but not persisted).
     */
    public void markDirty() {
        this.dirty = true;
        this.lastWrite = System.currentTimeMillis();
    }
    
    /**
     * Mark this entry as clean (persisted to database).
     */
    public void markClean() {
        this.dirty = false;
    }
    
    /**
     * Check if this entry is expired based on TTL.
     * 
     * @param ttlMillis TTL in milliseconds
     * @return true if expired
     */
    public boolean isExpired(long ttlMillis) {
        return (System.currentTimeMillis() - lastWrite) > ttlMillis;
    }
}
