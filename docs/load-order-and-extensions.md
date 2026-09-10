# Load Order & Extensions

How VInject orders startup and how to extend annotation processing.

---

## Load Phases

The analyzer produces a load plan consumed by `DependencyContainer`. Key phase boundaries:

| Phase | What loads |
| --- | --- |
| Early | YAML configs, serializers, interceptors |
| Services | `@Service` classes, then their `@Bean` methods |
| Repositories | `@Repository` interface proxies |
| Components | Registry targets, then `@Component` classes |

**Critical rule:** YAML configuration classes cannot depend on components, services, repositories, or registry targets. See `VINJECT-DEP-004` in [VInject-Analyzer README](../VInject-Analyzer/README.md).

---

## `@Registry` Extension Point

Custom annotations are processed by classes extending `AnnotationHandler`:

```java
@Registry(annotation = MyCustomAnnotation.class, order = RegistryOrder.COMPONENTS)
public class MyCustomHandler extends AnnotationHandler {
    @Override
    public void handle(Class<?> clazz, @Nullable Object instance, DependencyContainer container) {
        Object obj = instance != null ? instance : container.newInstance(clazz);
        // register obj with external system
    }
}
```

### `RegistryOrder`

| Order | Typical use |
| --- | --- |
| `ENTITIES` | Entity-related registration |
| `FIRST` | Early hooks |
| `SERVICES` | After services loaded |
| `REPOSITORIES` | After repositories |
| `COMPONENTS` | Commands, listeners, API facades (VortexCore uses this) |

Handlers must be on the classpath inside the `@Root` scan scope (or `includedPackages`).

VortexCore handlers live in `net.vortexdevelopment.vortexcore.vinject.handler`:
- `CommandHandler` - `@Command`
- `RegisterListenerHandler` - `@RegisterListener`
- `ApiHandler` - `@Api`
- `ReloadRegisterHookHandler` - `@RegisterReloadHook`

---

## `ComponentInterceptor`

Implement `ComponentInterceptor` to hook component registration:

```java
@Component
public class ReloadHookInterceptor implements ComponentInterceptor {
    @Override
    public void onComponentRegistered(Class<?> clazz, Object instance, DependencyContainer container) {
        if (instance instanceof ReloadHook hook) {
            // register hook
        }
    }
}
```

Interceptors are collected early so they can observe later component registration.

---

## `@Element` Collection

Classes annotated `@Element` are gathered into injectable `List<ElementType>` collections. See [components-and-injection.md](components-and-injection.md).

---

## Diagnostic Codes

The Maven plugin and runtime analyzer emit structured diagnostics. Common codes:

| Code | Meaning |
| --- | --- |
| `VINJECT-DEP-001` | Missing required dependency |
| `VINJECT-DEP-002` | Ambiguous dependency - multiple providers for one type |
| `VINJECT-DEP-004` | YAML class depends on late-phase provider |
| `VINJECT-DEP-005` | Named dependency via `@Qualifier` not found |
| `VINJECT-CYCLE-001` | Hard constructor cycle |
| `VINJECT-REG-001` | `@Registry` class does not extend `AnnotationHandler` |

Full list: [VInject-Analyzer README](../VInject-Analyzer/README.md).

---

## Key Source Files

| File | Purpose |
| --- | --- |
| `VInject-Core/.../DependencyContainer.java` | Authoritative load sequence |
| `VInject-Core/.../LifecycleManager.java` | `@PostConstruct`, `@OnLoad`, `@OnDestroy` |
| `VInject-Core/.../ConfigurationContainer.java` | YAML phase + `@OnLoad` on configs |
| `VInject-Analyzer/.../VinjectAnalyzer.java` | Static analysis and load plan |

---

## Related

- [lifecycle-and-events.md](lifecycle-and-events.md)
- [agent-reference.md](agent-reference.md)
