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

@Entity(table = "users")
public class User {

    @Id
    private UUID id; // Object wrapper (UUID), not primitive

    @Column(name = "username", length = 32)
    private String username;

    @Column(name = "coins")
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

## 4. Indexes (`@Index`)

Use `@Index` directly on a persisted field for a single-column index:

```java
@Index
@Column(name = "island_id", nullable = false)
private UUID islandId;
```

Use repeatable class-level annotations for composite indexes. Entries in `columns`
may use either Java field names or physical `@Column(name = "...")` names:

```java
@Entity(table = "island_upgrades")
@Index(columns = {"islandId", "upgrade_type"}, unique = true)
public class IslandUpgrade {
    @Id
    private UUID id;

    @Column(name = "island_id", nullable = false)
    private UUID islandId;

    @Column(name = "upgrade_type", nullable = false)
    private String upgradeType;
}
```

Column order is preserved. VInject generates a deterministic name when `name` is blank;
set an explicit name when an external schema requires one.

---

## 5. Foreign Keys (`@ForeignKey`)

Scalar ID fields declare the referenced entity explicitly. A blank `referencedColumn`
uses the entity's primary key; an explicit value can use its Java field or column name:

```java
@ForeignKey(
    entity = Island.class,
    onDelete = ForeignKeyAction.CASCADE,
    onUpdate = ForeignKeyAction.CASCADE
)
@Column(name = "island_id", nullable = false)
private UUID islandId;
```

Entity-valued fields infer the target entity:

```java
@ForeignKey(onDelete = ForeignKeyAction.CASCADE)
@Column(name = "island_id", nullable = false)
private Island island;
```

Use `SET_NULL` for optional relationships; the local column must be nullable:

```java
@ForeignKey(
    entity = Island.class,
    referencedColumn = "id",
    onDelete = ForeignKeyAction.SET_NULL
)
@Column(name = "island_id", nullable = true)
private UUID islandId;
```

Supported actions are `NO_ACTION`, `RESTRICT`, `CASCADE`, and `SET_NULL` for both
deletes and updates. Referenced fields must be primary or unique, and scalar field
types must match.

VInject creates missing declared indexes and constraints during schema verification and
replaces a same-named object when its declared shape changes. Removing an annotation does
not drop the existing schema object; use an explicit migration for destructive cleanup.

The Maven analyzer and IntelliJ inspection flag misspelled field/column references,
malformed index declarations, illegal `SET_NULL`, type mismatches, non-key targets, and
dangerous delete cycles. A multi-entity cycle containing `RESTRICT` or `NO_ACTION` is an
error, while an all-`CASCADE` cycle is a warning. `SET_NULL` breaks the delete cycle, and
self-referencing relationships remain valid for hierarchical data such as `parent_id`.

---

## 6. Entity Dirty Field Tracking (`@CachedField`)

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

## 7. Caching & Caching Coordinator

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

### Reference-counted pinning

Use `CachedCrudRepository` when an entity must remain in memory while it is in
active use. Each call to `pin()` increments the entry's pin count and each call
to `unpin()` decrements it. An entry with a positive count is not evicted by
TTL or size-based policies.

```java
@Repository
public interface IslandRepository
        extends CachedCrudRepository<IslandData, Long> {
    IslandData findByMemberUuid(UUID playerUuid);
}

IslandData island = repository.findByMemberUuid(playerUuid);
repository.pin(island, "member-online");

// On player leave:
repository.unpin(island, "member-online");
```

When the count reaches zero, the entity becomes eligible for the configured
eviction policy again. The `reason` is optional diagnostic metadata; pinning is
based on the total reference count.

---

## 8. Custom Database Type Serializers

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
