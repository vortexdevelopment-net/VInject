# Lifecycle & Events

How VInject initializes and tears down managed objects.

---

## Startup Timeline

When `DependencyContainer` is constructed (via `VInjectApplication.run()` or `VortexPlugin.onEnable()`):

```
1. Core services (Database, EventManager, ConfigurationContainer, ...)
2. YAML configs (@YamlConfiguration / @YamlDirectory)
3. @Element classes collected
4. Registry handlers @ ENTITIES
5. @RegisterDatabaseSerializer
6. @Repository interfaces proxied
7. DB table init
8. ComponentInterceptors
9. @Registry handler classes registered
10. @ArgumentResolver processors
11. VinjectAnalyzer load plan
12. Registry @ FIRST
13. @Service classes + @Bean methods
14. Registry @ SERVICES / REPOSITORIES
15. Component load order:
    a. Registry handlers for annotated targets (e.g. @Command, @RegisterListener)
    b. @Component registration (if @Component present)
16. Root instance field injection
17. @OnDestroy method scan
```

---

## `@PostConstruct`

Runs **immediately after** field/constructor injection when an instance is created via `newInstance()`.

```java
@Component
public class CacheWarmer {
    @Inject private UserRepository users;

    @PostConstruct
    public void warmCache() {
        users.preloadAll();
    }
}
```

- Return type must be `void`.
- Methods may accept parameters - resolved from the container (same as `@OnLoad`).
- Invoked for `@Component`, `@Service`, registry-created instances, and YAML singletons when reloaded.

**Use for:** one-time setup after dependencies are available.

---

## `@OnLoad`

Runs when a **YAML config object** or **database entity** is materialized - not for every `@Component` at startup.

Called from `ConfigurationContainer` after mapping YAML into an instance, and after entity field hydration.

```java
@YamlItem
public class LootTable {
    @YamlId private String id;

    @OnLoad
    public void validate() {
        // runs after YAML fields are mapped
    }
}
```

**Do not use `@OnLoad` as a general "component started" hook** - use `@PostConstruct` for that.

---

## `@OnDestroy`

Runs during application shutdown (shutdown hook in `VInjectApplication`, or plugin disable).

```java
@Component
public class ConnectionPool {
    @OnDestroy
    public void close() {
        // cleanup
    }
}
```

Methods are collected at the end of container construction and invoked on shutdown.

---

## `@OnEvent`

Registers internal framework event listeners on a class. Parameters are resolved at invocation time.

```java
@Component
public class MetricsCollector {
    @OnEvent("user.registered")
    public void onUserRegistered(User user) {
        // handle event
    }
}
```

Emit events from code:

```java
DependencyRepository.getInstance().getEventManager().emitEvent("user.registered", user);
```

`EventManager.registerEventListeners(Class)` is called automatically for `@Service` and `@Component` classes during load.

---

## Lifecycle Comparison

| Hook | When | Typical target |
| --- | --- | --- |
| `@PostConstruct` | After DI on `newInstance()` | `@Component`, `@Service`, commands/listeners |
| `@OnLoad` | After YAML/entity field mapping | `@YamlItem`, `@Entity`, config DTOs |
| `@OnDestroy` | Shutdown / plugin disable | Any registered instance |
| `@OnEvent` | When `emitEvent()` is called | `@Component`, `@Service` |

---

## Parameterized Lifecycle Methods

Both `@PostConstruct` and `@OnLoad` support parameters injected from the container:

```java
@PostConstruct
public void init(AuditLogger logger, @Value("${app.mode:prod}") String mode) { }
```

---

## Related

- [components-and-injection.md](components-and-injection.md) - injection rules
- [yaml_configuration.md](yaml_configuration.md) - config mapping
- [load-order-and-extensions.md](load-order-and-extensions.md) - phase details
