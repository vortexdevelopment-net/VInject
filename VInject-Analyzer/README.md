# VInject Analyzer

`VInject-Analyzer` is the shared dependency analysis module used by runtime loading, the Maven plugin, and IDE integrations. It discovers VInject components, services, repositories, beans, YAML configuration classes, registry handlers, dependencies, and dependency load order.

The analyzer reports diagnostics with stable codes so every integration can show the same errors and warnings.

Codes and message templates are defined in `net.vortexdevelopment.vinject.analyzer.diagnostic.DiagnosticCode`. Use the enum when creating diagnostics instead of hardcoding raw `VINJECT-*` strings or diagnostic messages inline.

## Diagnostic Format

Diagnostics are formatted as:

```text
<severity> <code> <location> - <message>
```

Severity behavior:

- `ERROR`: invalid VInject wiring. The Maven plugin fails the build when any error is present.
- `WARNING`: suspicious or incomplete analysis. The Maven plugin prints the warning but does not fail the build.

## Error Codes

| Code | Severity | Description | Typical Fix |
| --- | --- | --- | --- |
| `VINJECT-ANALYZER-001` | Error | Analysis was requested without either explicit candidate classes or a `@Root` annotation/root class pair. | Provide candidate classes in the request, or configure analysis with the application root class and its `@Root` annotation. |
| `VINJECT-COND-001` | Error | A load condition or load predicate failed with a runtime exception while deciding whether a class can load. | Fix the condition logic or the dependency that condition evaluation needs. |
| `VINJECT-COND-002` | Error | The analyzer could not evaluate load conditions for a class because inspection failed unexpectedly. | Make sure the class and all types referenced by its conditions are available on the analyzer classpath. |
| `VINJECT-REG-001` | Error | A class annotated with `@Registry` does not extend `AnnotationHandler`. | Make the registry class extend `net.vortexdevelopment.vinject.di.registry.AnnotationHandler`. |
| `VINJECT-BEAN-001` | Error | A `@Bean` method returns `void`. | Change the method to return the bean type it creates. |
| `VINJECT-BEAN-003` | Error | A `@Service` class with `@Bean` methods does not have a default constructor. | Add a no-argument constructor, or move bean creation to a service that can be instantiated without constructor dependencies. |
| `VINJECT-DEP-001` | Error | A required dependency has no provider. | Add a matching component/service/repository/bean, register the dependency externally, or mark it optional if null is valid. |
| `VINJECT-DEP-002` | Error | A required dependency has multiple possible providers. | Inject the concrete class, add `@Qualifier("name")` on the injection point, or name one producer with `@Component(name)` / `@Qualifier`. |
| `VINJECT-DEP-005` | Error | A named dependency requested via `@Qualifier` was not found. | Add a producer with matching `@Component(name)` / `@Qualifier`, or fix the qualifier string on the injection point. |
| `VINJECT-DEP-004` | Error | A YAML configuration class depends on a provider that is loaded later, such as a component, service, bean method, registry target, or repository. YAML configuration objects are created before those providers exist. | Move that logic out of the YAML config class, inject the YAML config into a later component/service instead, or depend only on built-in/pre-registered early-load types. |
| `VINJECT-CYCLE-001` | Error | A hard dependency cycle was found through constructor, `@Bean`, or lifecycle dependencies. | Break the cycle by moving one dependency to field/setter injection, introducing a lazy lookup, or extracting shared state into a separate provider. |

## Warning Codes

| Code | Severity | Description | Typical Fix |
| --- | --- | --- | --- |
| `VINJECT-DEP-003` | Warning | An optional dependency could not be resolved. | No fix is required if null is expected. Otherwise add a provider or remove the optional marker. |
| `VINJECT-ANALYZER-002` | Warning | Methods could not be inspected for a class because a referenced type was missing from the analyzer classpath. | Add the missing dependency to the compile/test classpath used by the analyzer. |
| `VINJECT-ANALYZER-003` | Warning | Fields could not be inspected for a class because a referenced type was missing from the analyzer classpath. | Add the missing dependency to the compile/test classpath used by the analyzer. |
| `VINJECT-ANALYZER-004` | Warning | Constructors could not be inspected for a class because a referenced type was missing from the analyzer classpath. | Add the missing dependency to the compile/test classpath used by the analyzer. |
| `VINJECT-ANALYZER-005` | Warning | The default constructor check could not be completed because a referenced type was missing from the analyzer classpath. | Add the missing dependency to the compile/test classpath used by the analyzer. |
| `VINJECT-CYCLE-002` | Warning | A field/setter dependency cycle was found. Runtime can defer these cycles, but they may still make initialization harder to reason about. | Prefer an acyclic dependency direction where possible, or keep the cycle only when deferred injection is intentional. |

## Load Phase Notes

VInject does not create every provider at the same time. The analyzer models the most important phase boundary:

- YAML configuration classes are created early.
- Repositories, components, services, bean methods, and registry targets are loaded later.

Because of that, a YAML class like this is invalid and reports `VINJECT-DEP-004`:

```java
@Component
@YamlDirectory(dir = "categories", target = CategoryEntry.class)
public class CategoryConfig {
    @Inject
    private BazaarOrderRepository orderRepository;
}
```

The usual shape is to keep YAML classes as configuration holders and inject them into a later component/service:

```java
@Component
public class CategoryVerifier {
    @Inject
    private CategoryConfig categoryConfig;

    @Inject
    private BazaarOrderRepository orderRepository;
}
```

## Maven Plugin Behavior

`vinject-maven-plugin` runs the analyzer during `process-classes` and, when configured, `process-test-classes`.

The plugin:

- prints all diagnostics grouped by Maven log severity,
- throws `MojoExecutionException` when any `ERROR` diagnostic exists,
- continues past warnings,
- runs bytecode transformations only after analysis passes.
