# Components & Injection

This guide covers how VInject discovers, creates, and wires classes. For lifecycle hooks, see [lifecycle-and-events.md](lifecycle-and-events.md).

---

## `@Root` - Application Entry Point

Mark your main class with `@Root` to define the classpath scan root.

```java
@Root(packageName = "org.example.myplugin")
public class MyApplication { }
```

| Attribute | Purpose |
| --- | --- |
| `packageName` | Root package to scan. Empty = auto-detect from the root class package. |
| `ignoredPackages` | Sub-packages excluded from scanning. |
| `includedPackages` | Extra packages to include (e.g. shaded `vortexcore` under a relocated path). |
| `createInstance` | Whether VInject instantiates the root class (default `true`). Set `false` when the root is `VortexPlugin`. |
| `componentAnnotations` | **IDE-only** (IntelliJ plugin). Does not affect runtime scanning. |
| `templateDependencies` | **IDE-only**. Registers file templates from dependency artifacts. |

---

## Component Types

| Annotation | Use for |
| --- | --- |
| `@Component` | General managed singletons (managers, helpers, handlers). |
| `@Service` | Business logic layer. Supports `@Bean` factory methods. |
| `@Repository` | Database repository interfaces (auto-proxied). |

### Auto-registration and `registerSubclasses`

`@Component` classes automatically register under all inherited interfaces and superclasses (except `Object`, `Serializable`, etc.). You can `@Inject` an interface when only one component implements it - no `registerSubclasses` required:

```java
@Component
public class StackedEntityManagerImpl implements StackedEntityManager { }

@Component
public class Consumer {
    @Inject private StackedEntityManager manager; // works automatically
}
```

`registerSubclasses` remains optional for **extra** type aliases beyond auto-discovered super-types.

### Named beans and `@Qualifier`

When multiple beans share a type, name producers and select with `@Qualifier`:

```java
@Component(name = "primary")
public class PrimaryImpl implements Port { }

@Qualifier("secondary")
@Component
public class SecondaryImpl implements Port { }

@Component
public class Consumer {
    @Inject @Qualifier("secondary")
    private Port port;
}
```

Without `@Qualifier`, injecting a shared interface reports `VINJECT-DEP-002` at build time and fails at runtime. Inject the **concrete class** to pick a specific implementation by type.

### Priority

`@Component(priority = N)` - lower numbers load earlier within the component phase.

---

## Injection Modes

`@Inject` works on fields, constructors, and setter methods.

```java
@Component
public class OrderService {
    @Inject private OrderRepository repository;           // field (preferred for breaking cycles)

    @Inject
    public OrderService(PaymentGateway gateway) { }       // constructor

    @Inject
    public void setAuditLogger(AuditLogger logger) { }    // setter
}
```

### Rules

- **Field injection** can break circular dependencies (deferred until after instantiation).
- **Constructor injection** cannot participate in cycles - startup fails with `VINJECT-CYCLE-001`.
- Use `@OptionalDependency` when null is acceptable if no provider exists.
- Use `@Value("${key:default}")` for property injection.

---

## Dependency Control

| Annotation | Behavior |
| --- | --- |
| `@DependsOn(A.class)` | Hard: class A must load first; missing provider = error. |
| `@DependsOn(value = A.class, soft = true)` | Soft: skip loading this component if A is unavailable. |
| `@Conditional(...)` | Skip component when condition does not match. |
| `@Order` | Execution precedence for ordered collections. |

---

## The Golden Rule

**Never use `new` on framework-managed classes** (`@Component`, `@Service`, `@Repository`).

```java
// WRONG
MyService service = new MyService();

// RIGHT
MyService service = container.getDependency(MyService.class);
// or @Inject MyService in another managed class
```

Using `new` bypasses injection, `@PostConstruct`, and container caching.

---

## Registry Annotations Are Not `@Component`

VortexCore annotations like `@RegisterListener`, `@Command`, `@Api`, and `@RegisterReloadHook` are processed by `@Registry` handlers - they are **not** meta-annotated with `@Component`.

At runtime:

1. The registry handler runs and calls `newInstance()` on the class.
2. `@Inject` fields are resolved during `newInstance()`.
3. `@Component` registration only happens if `@Component` is explicitly present.

**Do not add `@Component` just because a listener or command has `@Inject` fields.**

Add `@Component` only when:

- Another class must `@Inject` that exact type as a singleton.
- You need `ReloadHook` auto-registration via `ReloadHookInterceptor` on a `@Component`.

See [VortexCore annotations.md](../../VortexCore/docs/annotations.md) for plugin-specific guidance.

---

## `@Element` - Collecting Implementations

Mark implementations with `@Element` and inject a collection:

```java
@Element
public class FileAuditLogger implements AuditLogger { }

@Element
public class DatabaseAuditLogger implements AuditLogger { }

@Component
public class AuditCoordinator {
    @Inject
    private List<AuditLogger> loggers; // all @Element implementations
}
```

Use `DependencyRepository.getInstance().collectElements(SomeType.class)` for manual collection.

---

## YAML Config Classes

`@YamlConfiguration` and `@YamlDirectory` holders are created during the **early YAML phase** - before components, services, and repositories.

A YAML config class **must not** `@Inject` later-phase providers (components, repositories). This reports `VINJECT-DEP-004`.

```java
// WRONG
@YamlConfiguration(file = "config.yml")
public class BadConfig {
    @Inject private OrderRepository orders; // loaded too early
}

// RIGHT
@Component
public class OrderVerifier {
    @Inject private MyConfig config;
    @Inject private OrderRepository orders;
}
```

---

## Related

- [lifecycle-and-events.md](lifecycle-and-events.md) - when hooks fire
- [load-order-and-extensions.md](load-order-and-extensions.md) - full startup order
- [agent-reference.md](agent-reference.md) - quick lookup
