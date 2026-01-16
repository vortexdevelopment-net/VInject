package net.vortexdevelopment.vinject.database.cache;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.vortexdevelopment.vinject.debug.DebugLogger;

/**
 * Configuration class for cache behavior.
 * Built from @EnableCaching annotation or YAML configuration.
 */
@Getter
@Setter
@Builder
public class CacheConfig {
    private CachePolicy policy;
    private int maxSize;
    private int hotTierSize;
    private long ttlSeconds;
    private WriteStrategy writeStrategy;
    private int flushIntervalSeconds;
    private boolean enabled;
    
    /**
     * Creates a default configuration with sensible defaults.
     */
    public static CacheConfig defaults() {
        DebugLogger.log(CacheConfig.class, "Creating default cache configuration");
        CacheConfig config = CacheConfig.builder()
                .policy(CachePolicy.LRU)
                .maxSize(1000)
                .hotTierSize(100)
                .ttlSeconds(300)
                .writeStrategy(WriteStrategy.WRITE_THROUGH)
                .flushIntervalSeconds(10)
                .enabled(true)
                .build();
        DebugLogger.log(CacheConfig.class, "Default config: policy=%s, maxSize=%d, writeStrategy=%s", 
                config.policy.name(), config.maxSize, config.writeStrategy.name());
        return config;
    }
}
