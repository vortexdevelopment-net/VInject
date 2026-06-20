# VInject Documentation

Guides for the VInject dependency injection and configuration framework.

## Guides

| Guide | Topic |
| --- | --- |
| [Introduction & DI Concepts](introduction.md) | DI basics, `@Component` / `@Service` / `@Bean`, golden rules |
| [Components & Injection](components-and-injection.md) | `@Root`, injection modes, auto-registration, `@Qualifier`, dependency rules |
| [Lifecycle & Events](lifecycle-and-events.md) | `@PostConstruct`, `@OnLoad`, `@OnDestroy`, `@OnEvent` |
| [Load Order & Extensions](load-order-and-extensions.md) | Startup phases, `@Registry`, `@Element`, interceptors |
| [Features & Annotation Catalog](features.md) | Complete annotation reference |
| [YAML Configuration](yaml_configuration.md) | `@YamlConfiguration`, `@YamlDirectory`, serializers |
| [Database & Repositories](database_and_repositories.md) | `@Entity`, `@Repository`, ORM |
| [Testing](testing.md) | Unit and integration testing |
| [Caching & Debugging](caching_and_debugging.md) | `@EnableCaching`, `@EnableDebug` |

## Agent Quick Start

When working on VInject code or Vinject-backed plugins:

1. Read [agent-reference.md](agent-reference.md) for a one-page lookup.
2. For component/DI questions, read [components-and-injection.md](components-and-injection.md).
3. For startup hooks, read [lifecycle-and-events.md](lifecycle-and-events.md).
4. For `VINJECT-*` build errors, see [VInject-Analyzer README](../VInject-Analyzer/README.md).

For Minecraft plugin development on top of VortexCore, use the plugindev skill in the VortexCore repo: `.cursor/skills/vortex-plugindev/SKILL.md`.
