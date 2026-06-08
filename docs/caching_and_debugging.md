# Caching & Debugging Guide

VInject includes an integrated, high-performance database caching layer and a granular debug logging system. This guide covers how to configure and troubleshoot these features.

---

## 1. Caching Configuration

To configure caching on a database repository, annotate the repository interface with `@EnableCaching`.

```java
@Repository
@EnableCaching(
    policy = CachePolicy.HOT_AWARE,
    maxSize = 1000,
    hotTierSize = 100,
    writeStrategy = WriteStrategy.WRITE_THROUGH
)
public interface PlayerRepository extends CrudRepository<Player, UUID> {}
```

### Cache Policies
* **`CachePolicy.LRU`**: Uses a Least Recently Used map structure (`SimpleLRUCache`). Once the cache size reaches `maxSize`, the least recently accessed items are evicted.
* **`CachePolicy.HOT_AWARE`**: Uses a two-tier cache (`TwoTierCache`). It separates frequently accessed items ("hot tier") from standard items ("normal tier"). This is ideal for active players on a Minecraft server, ensuring active data remains cached in high-priority memory.

### Write Strategies
* **`WriteStrategy.WRITE_THROUGH`**: Data is saved to the database immediately during a repository `save()` operation, and the cache is updated synchronously.
* **`WriteStrategy.WRITE_BACK`**: Data is written directly to the cache, and database updates are queued. The repository writes dirty entities back to the database asynchronously, reducing main-thread database calls.

---

## 2. Debug Logging System

VInject has a built-in `DebugLogger` that helps you monitor container loading, class scanning, database queries, and cache hits/misses.

### A. Enabling Debug per Component (`@EnableDebug`)
To see debug logs for a specific class, annotate it with `@EnableDebug`:

```java
@EnableDebug
@Component
public class PlayerService {
    public void loadPlayer(UUID uuid) {
        DebugLogger.log("Loading profile for player: %s", uuid);
    }
}
```

### B. Enabling Debug for Other Classes (`@EnableDebugFor`)
If you want to view logs for internal VInject classes or third-party components, use `@EnableDebugFor` on one of your main classes:

```java
@EnableDebugFor({
    CacheManager.class,
    CacheConfig.class,
    TwoTierCache.class,
    PlayerRepository.class
})
@Component
public class MyTestApp {}
```

### C. Global Debugging via System Property
To enable all debug logs across the entire framework (including registry scanners, classloaders, and database transactions), run the JVM with the following system property:

```bash
java -Dvinject.debug.all=true -jar your-app.jar
```

---

## 3. What to Watch For in Cache Logs

When debugging cache behavior, enable debug logging for `CacheManager`, `CacheCoordinator`, and your repository classes. You will see output patterns like:

```text
[DEBUG:CacheConfig] Creating default cache configuration
[DEBUG:CacheManager] Creating cache for PlayerRepository with policy HOT_AWARE
[DEBUG:TwoTierCache] Initializing HOT tier (size=100) and NORMAL tier (size=1000)
[DEBUG:PlayerRepository] Cache HIT for Player[id=d12f38a5-d8aa-462a-886d-0bb291a27e33]
[DEBUG:PlayerRepository] Cache MISS for Player[id=fc985162-81ab-41c1-90a6-512c0192ea12], querying database
[DEBUG:TwoTierCache] Promoting Player[id=d12f38a5-d8aa-462a-886d-0bb291a27e33] to HOT tier (access count: 11)
```
