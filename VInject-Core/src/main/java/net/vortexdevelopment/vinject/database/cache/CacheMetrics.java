package net.vortexdevelopment.vinject.database.cache;

import lombok.Builder;
import lombok.Getter;

/**
 * Cache metrics data class.
 * Provides statistics about cache performance.
 */
@Getter
@Builder
public class CacheMetrics {
    private final long hits;
    private final long misses;
    private final long evictions;
    private final int size;
    private final double hitRate;
    
    /**
     * Calculate hit rate from hits and misses.
     */
    public static double calculateHitRate(long hits, long misses) {
        long total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
