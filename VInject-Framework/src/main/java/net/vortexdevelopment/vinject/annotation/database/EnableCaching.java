package net.vortexdevelopment.vinject.annotation.database;

import net.vortexdevelopment.vinject.database.cache.CachePolicy;
import net.vortexdevelopment.vinject.database.cache.WriteStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enable caching on a repository interface.
 * Cache configuration can be overridden by YAML configuration beans.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnableCaching {
    
    /**
     * Cache policy to use.
     * Default: LRU (Least Recently Used)
     */
    CachePolicy policy() default CachePolicy.LRU;
    
    /**
     * Maximum number of entities in NORMAL tier (or total if single-tier).
     * Default: 1000
     */
    int maxSize() default 1000;
    
    /**
     * Maximum number of entities in HOT tier (only for HOT_AWARE policy).
     * Default: 100
     */
    int hotTierSize() default 100;
    
    /**
     * Time-to-live for cached entries in seconds (only for TTL policy).
     * Default: 300 seconds (5 minutes)
     */
    long ttlSeconds() default 300;
    
    /**
     * Write strategy: WRITE_THROUGH or WRITE_BACK.
     * Default: WRITE_THROUGH (safer)
     */
    WriteStrategy writeStrategy() default WriteStrategy.WRITE_THROUGH;
    
    /**
     * Flush interval in seconds for WRITE_BACK strategy.
     * Default: 10 seconds
     */
    int flushIntervalSeconds() default 10;
    
    /**
     * Whether caching is enabled.
     * Can be used to temporarily disable caching without removing the annotation.
     * Default: true
     */
    boolean enabled() default true;
}
