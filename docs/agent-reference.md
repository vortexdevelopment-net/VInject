# Agent Reference

One-page lookup for VInject. Read topic guides for full detail.

---

## Annotation Quick Map

| Need | Annotation | Guide |
| --- | --- | --- |
| Scan root | `@Root` | [components-and-injection.md](components-and-injection.md) |
| Managed singleton | `@Component`, `@Service` | [components-and-injection.md](components-and-injection.md) |
| Inject dependency | `@Inject` | [components-and-injection.md](components-and-injection.md) |
| Interface binding | `@Component` (auto-registers inherited types) | [components-and-injection.md](components-and-injection.md) |
| Disambiguate multiple beans | `@Qualifier` on producer or injection point | [components-and-injection.md](components-and-injection.md) |
| After DI setup | `@PostConstruct` | [lifecycle-and-events.md](lifecycle-and-events.md) |
| After YAML/entity map | `@OnLoad` | [lifecycle-and-events.md](lifecycle-and-events.md) |
| Shutdown cleanup | `@OnDestroy` | [lifecycle-and-events.md](lifecycle-and-events.md) |
| Internal events | `@OnEvent` | [lifecycle-and-events.md](lifecycle-and-events.md) |
| Single YAML file | `@YamlConfiguration` | [yaml_configuration.md](yaml_configuration.md) |
| YAML directory batch | `@YamlDirectory` + `@YamlCollection` | [yaml_configuration.md](yaml_configuration.md) |
| DB table | `@Entity` + `@Repository` | [database_and_repositories.md](database_and_repositories.md) |
| Custom annotation processing | `@Registry` + `AnnotationHandler` | [load-order-and-extensions.md](load-order-and-extensions.md) |

---

## Load Order (Simplified)

```
YAML configs -> Services/Beans -> Repositories -> Registry handlers -> @Component
```

YAML classes must not `@Inject` repositories or components (`VINJECT-DEP-004`).

---

## Golden Rules

1. Never `new` managed classes - use `@Inject` or `container.getDependency()`.
2. `@OnLoad` is for YAML/entity hydration, not general startup - use `@PostConstruct`.
3. `@Root.componentAnnotations` is IDE-only, not runtime.
4. Registry annotations (`@Command`, `@RegisterListener`, etc.) are not `@Component` - do not add `@Component` unless another class must inject that type.
5. Field injection breaks cycles; constructor injection does not.

---

## Common Errors

| Error | Fix |
| --- | --- |
| `VINJECT-DEP-001` | Add provider or mark `@OptionalDependency` |
| `VINJECT-DEP-002` | Multiple providers for one type - inject concrete class or add `@Qualifier` |
| `VINJECT-DEP-004` | Move repository injection out of YAML config class |
| `VINJECT-DEP-005` | Fix `@Qualifier` name on consumer or add matching `@Component(name)` / `@Qualifier` on producer |
| `VINJECT-CYCLE-001` | Switch one side to field injection |
| Null `@Inject` field at runtime | Class was constructed with `new` instead of container |

---

## Source Files to Read

| Question | Read |
| --- | --- |
| Startup order | `VInject-Core/.../DependencyContainer.java` |
| Lifecycle hooks | `VInject-Core/.../LifecycleManager.java` |
| YAML loading | `VInject-Core/.../ConfigurationContainer.java` |
| Build diagnostics | `VInject-Analyzer/README.md` |
| Plugin conventions | `VortexCore/.cursor/skills/vortex-plugindev/SKILL.md` |

---

## Minecraft Plugins

VInject is the DI backbone; VortexCore adds commands, GUIs, messaging, and Bukkit integration. Use the VortexCore plugindev skill for plugin-specific patterns.
