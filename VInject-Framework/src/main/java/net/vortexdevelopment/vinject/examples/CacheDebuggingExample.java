package net.vortexdevelopment.vinject.examples;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.database.EnableCaching;
import net.vortexdevelopment.vinject.annotation.util.EnableDebug;
import net.vortexdevelopment.vinject.annotation.util.EnableDebugFor;
import net.vortexdevelopment.vinject.database.cache.CacheConfig;
import net.vortexdevelopment.vinject.database.cache.CachePolicy;
import net.vortexdevelopment.vinject.database.cache.WriteStrategy;
import net.vortexdevelopment.vinject.database.cache.config.GlobalCacheConfig;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.debug.DebugLogger;

/**
 * Example demonstrating cache debugging setup.
 * Use this to track cache behavior during development.
 */
public class CacheDebuggingExample {
    
    // Example 1: Enable debug for cache infrastructure
    @EnableDebugFor({
        CacheConfig.class,
        GlobalCacheConfig.class
        // Add cache implementation classes here when created:
        // CacheManager.class,
        // CachingRepository.class,
        // TwoTierCache.class,
        // etc.
    })
    @Component
    public static class TestApplication {
        public void run() {
            // Your test code here
            DebugLogger.log("Testing cache system");
        }
    }
    
    // Example 2: Repository with caching and debug enabled
    @EnableCaching(
        policy = CachePolicy.HOT_AWARE,
        maxSize = 1000,
        hotTierSize = 100,
        writeStrategy = WriteStrategy.WRITE_THROUGH
    )
    @EnableDebug  // Enable debug for this repository
    public interface UserRepository extends CrudRepository<User, Long> {
        // Custom query methods
    }
    
    // Example 3: Test entity
    public static class User {
        private Long id;
        private String name;
        
        public User(Long id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public Long getId() { return id; }
        public String getName() { return name; }
    }
    
    /**
     * Main method for standalone testing.
     * Run with: java -Dvinject.debug.all=true CacheDebuggingExample
     */
    public static void main(String[] args) {
        System.out.println("=== Cache Debugging Example ===");
        System.out.println("Enable debug for specific classes with @EnableDebugFor");
        System.out.println("Or run with -Dvinject.debug.all=true for all debug output");
        
        // Test default config creation
        CacheConfig defaultConfig = CacheConfig.defaults();
        System.out.println("\nCreated default config (check debug output above)");
    }
}
