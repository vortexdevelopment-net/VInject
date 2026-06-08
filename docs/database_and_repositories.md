# Database & Repository System

VInject includes a lightweight, built-in ORM and repository framework supporting MySQL, MariaDB, and H2 database backends, integrated with automatic caching.

---

## 1. Important Entity Schema Constraints

> [!WARNING]
> **Schema Constraint: No Primitive Types Allowed in Entity Classes!**
> Do **not** use Java primitives (e.g. `long`, `int`, `double`, `boolean`) in your `@Entity` classes. Always use their object wrappers (e.g. `Long`, `Integer`, `Double`, `Boolean`).

### Why?
1. **Default Value Collision**: Primitives have default values (e.g., `int` defaults to `0`, `boolean` to `false`). VInject cannot determine if a primitive `0` means the value is unset or if the value was explicitly set to `0`. Using object wrappers allows fields to be `null` when they are unset or not mapped.
2. **Database Updates**: During updates, VInject only writes fields that have changed. A primitive `false` or `0` can result in unwanted database updates that overwrite valid existing data with defaults.

---

## 2. Defining Entities (`@Entity`)

An entity represents a row in a database table.

```java
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import java.util.UUID;

@Entity(tableName = "users")
public class User {

    @Id
    private UUID id; // Object wrapper (UUID), not primitive

    @Column(columnName = "username", length = 32)
    private String username;

    @Column(columnName = "coins")
    private Integer coins = 0; // Object wrapper (Integer), not int

    // Getters and Setters (or Lombok @Getter/@Setter)
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getCoins() { return coins; }
    public void setCoins(Integer coins) { this.coins = coins; }
}
```

---

## 3. Repositories (`CrudRepository`)

To interact with the database, define an interface extending `CrudRepository<T, ID>` and annotate it with `@Repository`. VInject will automatically proxy the interface and generate the SQL queries.

```java
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<User, UUID> {
    
    // Dynamic finders are automatically resolved from method names
    User findByUsername(String username);
}
```

### Supported Default Methods
* `T save(T entity)`
* `T findById(ID id)`
* `List<T> findAll()`
* `void delete(T entity)`
* `void deleteById(ID id)`

---

## 4. Entity Dirty Field Tracking (`@CachedField`)

To optimize database write operations, you can track which fields have been modified in memory so that `save()` only updates changed columns.

1. Annotate modification methods on your Entity with `@CachedField("fieldName")`.
2. Ensure the `vinject-maven-plugin` is active in your `pom.xml`.

```java
import net.vortexdevelopment.vinject.annotation.database.CachedField;

@Entity
public class User {
    // ... fields ...

    @CachedField("coins")
    public void addCoins(int amount) {
        this.coins = (this.coins == null ? 0 : this.coins) + amount;
    }
}
```

---

## 5. Caching & Caching Coordinator

Enable repository-level caching by annotating your `@Repository` interface with `@EnableCaching`.

```java
import net.vortexdevelopment.vinject.annotation.database.EnableCaching;
import net.vortexdevelopment.vinject.database.cache.CachePolicy;
import net.vortexdevelopment.vinject.database.cache.WriteStrategy;

@Repository
@EnableCaching(
    policy = CachePolicy.HOT_AWARE,
    maxSize = 1000,
    hotTierSize = 100,
    writeStrategy = WriteStrategy.WRITE_THROUGH
)
public interface UserRepository extends CrudRepository<User, UUID> {}
```

### Caching Policies
* `CachePolicy.LRU`: Standard Least Recently Used cache eviction.
* `CachePolicy.HOT_AWARE`: Utilizes a two-tier cache (`TwoTierCache`) separating frequently accessed "hot" entities from normal entities.

### Write Strategies
* `WriteStrategy.WRITE_THROUGH`: Writes to both cache and database immediately.
* `WriteStrategy.WRITE_BACK`: Writes updates to cache first and batches database writes asynchronously.

---

## 6. Custom Database Type Serializers

If an entity contains a custom type that cannot be directly mapped to SQL columns, register a custom serializer.

1. Implement `DatabaseSerializer<T, S>` where `T` is the Java type and `S` is the SQL-compatible type.
2. Annotate it with `@RegisterDatabaseSerializer`.

```java
import net.vortexdevelopment.vinject.annotation.database.RegisterDatabaseSerializer;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;

public class CustomCoordinates {
    private final int x, z;
    public CustomCoordinates(int x, int z) { this.x = x; this.z = z; }
    public String serialize() { return x + "," + z; }
}

@RegisterDatabaseSerializer(CustomCoordinates.class)
public class CoordinatesSerializer implements DatabaseSerializer<CustomCoordinates, String> {
    @Override
    public String toDatabase(CustomCoordinates object) {
        return object == null ? null : object.serialize();
    }

    @Override
    public CustomCoordinates toField(String databaseValue) {
        if (databaseValue == null) return null;
        String[] parts = databaseValue.split(",");
        return new CustomCoordinates(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
```
