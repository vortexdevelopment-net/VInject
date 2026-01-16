# Cache Debugging Guide

## Quick Start

### Enable Debug for All Cache Components

```java
@EnableDebugFor({
    CacheConfig.class,
    GlobalCacheConfig.class,
    // When implemented:
    // CacheManager.class,
    // CachingRepository.class,
    // SimpleLRUCache.class,
    // TwoTierCache.class
})
@Component
public class MyTestApp {
}
```

### Enable Debug Globally via System Property

```bash
java -Dvinject.debug.all=true -jar your-app.jar
```

## What to Watch For

When cache system is fully implemented, you'll see debug output like:

### Configuration Loading
```
[DEBUG:CacheConfig] Creating default cache configuration
[DEBUG:CacheConfig] Default config: policy=LRU, maxSize=1000, writeStrategy=WRITE_THROUGH
[DEBUG:GlobalCacheConfig] Loading cache configuration from cache.yml
[DEBUG:GlobalCacheConfig] Found 3 repository overrides
```

### Cache Operations (Future)
```
[DEBUG:CacheManager] Creating cache for UserRepository with policy HOT_AWARE
[DEBUG:TwoTierCache] Initializing HOT tier (size=100) and NORMAL tier (size=1000)
[DEBUG:CachingRepository] Cache HIT for User[id=123]
[DEBUG:CachingRepository] Cache MISS for User[id=456], querying database
[DEBUG:TwoTierCache] Promoting User[id=123] to HOT tier (access count: 11)
[DEBUG:CachingRepository] Write-back: 5 dirty entities pending flush
```

## Testing Cache Behavior

1. **Create a test repository**:
```java
@EnableCaching(policy = CachePolicy.HOT_AWARE, maxSize = 100)
@EnableDebug
public interface TestRepository extends CrudRepository<MyEntity, Long> {
}
```

2. **Run operations and observe debug output**:
```java
TestRepository repo = container.getDependency(TestRepository.class);

DebugLogger.log("Fetching user 1 (first time - should be MISS)");
User user1 = repo.findById(1L);

DebugLogger.log("Fetching user 1 again (should be HIT)");
User user1Again = repo.findById(1L);

DebugLogger.log("Saving user (watch write strategy behavior)");
repo.save(user1);
```

3. **Monitor cache statistics** (when CacheManager is implemented):
```java
CacheMetrics metrics = cacheManager.getMetrics("TestRepository");
DebugLogger.log("Hits: %d, Misses: %d, Hit Rate: %.2f%%", 
    metrics.getHits(), metrics.getMisses(), metrics.getHitRate() * 100);
```

## Debug by Component

Enable debug selectively for troubleshooting:

```java
// Debug only cache config resolution
@EnableDebugFor(CacheConfig.class)

// Debug only cache operations
@EnableDebugFor({CacheManager.class, CachingRepository.class})

// Debug only specific cache implementation
@EnableDebugFor(TwoTierCache.class)
```
