package net.vortexdevelopment.vinject.database.cache.config;

import lombok.Getter;
import lombok.Setter;
import net.vortexdevelopment.vinject.database.cache.CachePolicy;
import net.vortexdevelopment.vinject.database.cache.WriteStrategy;

/**
 * Bean class for per-repository cache configuration.
 * Used in YAML configuration to override caching behavior for specific repositories.
 */
@Getter
@Setter
public class RepositoryCacheConfig {
    /**
     * Fully qualified class name of the repository (e.g., "com.example.UserRepository").
     */
    private String repositoryClass;
    
    /**
     * Whether caching is enabled for this repository.
     * Set to false to disable caching entirely.
     */
    private Boolean enabled;
    
    /**
     * Cache policy to use.
     */
    private CachePolicy policy;
    
    /**
     * Maximum number of entities in cache (NORMAL tier or total).
     */
    private Integer maxSize;
    
    /**
     * Maximum number of entities in HOT tier (only for HOT_AWARE policy).
     */
    private Integer hotTierSize;
    
    /**
     * Time-to-live in seconds (only for TTL policy).
     */
    private Long ttlSeconds;
    
    /**
     * Write strategy: WRITE_THROUGH or WRITE_BACK.
     */
    private WriteStrategy writeStrategy;
    
    /**
     * Flush interval in seconds for WRITE_BACK strategy.
     */
    private Integer flushIntervalSeconds;
}
