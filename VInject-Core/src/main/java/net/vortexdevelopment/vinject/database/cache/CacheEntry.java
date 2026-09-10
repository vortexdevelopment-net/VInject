package net.vortexdevelopment.vinject.database.cache;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper for cached entities with metadata.
 * Tracks access patterns and dirty state for cache eviction and write-back strategies.
 */
@Getter
public class CacheEntry<T> {
    private volatile T value;
    private volatile long lastAccess;
    private volatile long lastWrite;
    private final AtomicInteger accessCount;
    private final AtomicInteger pinCount;
    private final Map<String, AtomicInteger> pinsByReason;
    private volatile boolean dirty;
    private volatile long unpinnedAt;
    
    public CacheEntry(T value) {
        this.value = value;
        this.lastAccess = System.currentTimeMillis();
        this.lastWrite = System.currentTimeMillis();
        this.accessCount = new AtomicInteger(0);
        this.pinCount = new AtomicInteger(0);
        this.pinsByReason = new ConcurrentHashMap<>();
        this.dirty = false;
        this.unpinnedAt = this.lastAccess;
    }
    
    /**
     * Mark this entry as accessed, updating timestamp and incrementing counter.
     */
    public void markAccessed() {
        long now = System.currentTimeMillis();
        this.lastAccess = now;
        if (!isPinned()) {
            this.unpinnedAt = now;
        }
        this.accessCount.incrementAndGet();
    }

    /**
     * Replaces the value while preserving access, pin, and dirty metadata.
     */
    public void replaceValue(T value) {
        this.value = value;
        this.lastWrite = System.currentTimeMillis();
    }

    /**
     * Adds one pin to this entry.
     *
     * @param reason optional diagnostic reason; may be null
     * @return the new total pin count
     */
    public int pin(String reason) {
        int count = pinCount.incrementAndGet();
        if (reason != null && !reason.isBlank()) {
            pinsByReason.computeIfAbsent(reason, ignored -> new AtomicInteger())
                    .incrementAndGet();
        }
        return count;
    }

    /**
     * Removes one pin from this entry.
     *
     * @param reason optional diagnostic reason; may be null
     * @return the remaining total pin count
     * @throws IllegalStateException when the entry has no pins
     */
    public int unpin(String reason) {
        int remaining = pinCount.decrementAndGet();
        if (remaining < 0) {
            pinCount.incrementAndGet();
            throw new IllegalStateException("Cache entry was unpinned too many times");
        }

        if (reason != null && !reason.isBlank()) {
            AtomicInteger reasonCount = pinsByReason.get(reason);
            if (reasonCount != null && reasonCount.decrementAndGet() <= 0) {
                pinsByReason.remove(reason, reasonCount);
            }
        }

        if (remaining == 0) {
            unpinnedAt = System.currentTimeMillis();
        }
        return remaining;
    }

    /**
     * Returns whether this entry is protected from eviction.
     */
    public boolean isPinned() {
        return pinCount.get() > 0;
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
        if (isPinned() || dirty) {
            return false;
        }
        return (System.currentTimeMillis() - unpinnedAt) > ttlMillis;
    }
}
