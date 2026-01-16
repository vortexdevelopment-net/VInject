package net.vortexdevelopment.vinject.database.cache.config;

import lombok.Getter;
import lombok.Setter;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.database.cache.CachePolicy;
import net.vortexdevelopment.vinject.database.cache.WriteStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean class for global default cache settings.
 * Loaded from cache.yml configuration file.
 */
@Getter
@Setter
@YamlConfiguration(file = "cache.yml")
public class GlobalCacheConfig {
    
    @Key("Enabled")
    private boolean enabled = true;
    
    @Key("Default Policy")
    private CachePolicy defaultPolicy = CachePolicy.LRU;
    
    @Key("Default Max Size")
    private int defaultMaxSize = 1000;
    
    @Key("Default Hot Tier Size")
    private int defaultHotTierSize = 100;
    
    @Key("Default TTL Seconds")
    private long defaultTtlSeconds = 300;
    
    @Key("Default Write Strategy")
    private WriteStrategy defaultWriteStrategy = WriteStrategy.WRITE_THROUGH;
    
    @Key("Default Flush Interval")
    private int defaultFlushInterval = 10;
    
    @Key("Repository Overrides")
    private List<RepositoryCacheConfig> repositoryOverrides = new ArrayList<>();
}
