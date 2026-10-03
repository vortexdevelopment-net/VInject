# VInject Annotation & Feature Reference Catalog

A comprehensive catalog of annotations and features available in the VInject framework, grouped by functionality.

---

## 1. Core Dependency Injection System

| Annotation | Target | Description |
| --- | --- | --- |
| `@Root` | Type | Marks the main application class. Triggers package scanning. Configuration properties: `packageName`, `ignoredPackages`, `includedPackages`, `createInstance`, `loadProperties`, `templateDependencies`. |
| `@Component` | Type | Managed singleton. Auto-registers inherited types. Optional `name`, `priority`. |
| `@Qualifier` | Type, Field, Parameter, Method | Bean name on producer or selector on injection point. |
| `@Service` | Type | Marks a class as a business-logic service. |
| `@Bean` | Method | Defines a factory method inside a component/service to register external or dynamically created classes. |
| `@Repository` | Type | Marks an interface as a database repository for automated query generation. |
| `@Inject` | Field / Constructor | Marks fields or constructors to receive dependencies from the container. |
| `@Value` | Field / Param | Injects configuration values using `${property.key:defaultValue}` syntax. |
| `@OptionalDependency` | Field / Param | Marks a dependency as optional (can be null if no provider is registered). |
| `@DependsOn` | Type | Controls startup load dependencies. Supports hard dependencies (throws if missing) and soft dependencies (skips loading component if missing). |
| `@Order` | Type | Determines precedence order for execution (e.g. servlet filters). |
| `@Element` | Type | Marks classes that can be scanned and injected as a collection (e.g., `List<MyElement>`). |
| `@SetSystemProperty` / `@SetSystemProperties` | Type | Sets system properties when the annotated component is loaded. |

---

## 2. Component Lifecycle Hooks

See [lifecycle-and-events.md](lifecycle-and-events.md) for full detail.

| Annotation | Target | Description |
| --- | --- | --- |
| `@PostConstruct` | Method | After DI completes on `newInstance()`. Use for general startup setup. Supports optional injected parameters. |
| `@OnLoad` | Method | After YAML fields or entity columns are mapped. **Not** a general component startup hook - use `@PostConstruct` for that. |
| `@OnDestroy` | Method | On application/plugin shutdown. |
| `@OnEvent` | Method | Internal event listener. Emit via `EventManager.emitEvent()`. Parameters resolved at invocation. |

---

## 3. Database, Caching & ORM System

| Annotation | Target | Description |
| --- | --- | --- |
| `@Entity` | Type | Maps a Java class to a database table. Fields must use wrapper types instead of primitives. |
| `@Id` | Field | Designates the primary key of an `@Entity`. |
| `@Column` | Field | Maps a field to a database column with specific options (e.g., `columnName`, `length`, `nullable`). |
| `@ColumnPrefix` | Field | Prepends a prefix to nested object column mappings. |
| `@Temporal` | Field | Configures temporal type mappings (Date, Time, Timestamp) for SQL queries. |
| `@AutoLoad` | Field | Configures proactive caching namespaces for specific entity fields. |
| `@Index` | Field, Type | Declares a single-column or ordered composite database index. |
| `@ForeignKey` | Field | Declares a foreign-key constraint with delete and update actions. |
| `@CachedField` | Method | Instructs the bytecode transformer to track field changes on entities for dirty tracking. |
| `@EnableCaching` | Type | Enables caching on repositories with configurable `policy` (LRU, HOT_AWARE) and `writeStrategy` (WRITE_THROUGH, WRITE_BACK). |
| `@RegisterCacheContributor` | Type | Registers a custom cache coordinator contributor. |
| `@RegisterDatabaseSerializer` | Type | Registers a custom `DatabaseSerializer` implementation for mapping custom types to SQL types. |

---

## 4. YAML Configuration System

| Annotation | Target | Description |
| --- | --- | --- |
| `@YamlConfiguration` | Type | Maps a single YAML file to a configuration component. Attributes: `file`, `path`, `autoSave`, `asyncSave`, `encoding`. |
| `@YamlDirectory` | Type | Configures batch configuration folder loading (e.g. loading all files in a directory as configuration objects). |
| `@YamlCollection` | Field | Denotes the collection field inside a `@YamlDirectory` class where mapped config entries are stored. |
| `@YamlId` | Field | Maps a configuration's file identifier (name) directly to a class field. |
| `@YamlItem` | Type | Designates a class as a compact YAML object structure. |
| `@Comment` | Type / Field | Prepends comments above fields or configurations when saved to disk. |
| `@NewLineBefore` / `@NewLineAfter` | Field | Adds empty lines around serialized fields on disk for readability. |
| `@YamlSerializer` | Type | Registers a custom `YamlSerializerBase` implementation to serialize complex field types to/from YAML. |
| `@YamlConditional` | Type | Skips registering a component unless a configuration value matches a specified criteria. |

---

## 5. Web HTTP Module (`VInject-HTTP`)

| Annotation | Target | Description |
| --- | --- | --- |
| `@RestController` | Type | Marks a class as a web request controller. Automatically scanned if the HTTP server is running. |
| `@RequestMapping` | Type / Method | Maps web requests to controller classes/methods. |
| `@GetMapping` | Method | Maps HTTP GET requests. |
| `@PostMapping` | Method | Maps HTTP POST requests. |
| `@PutMapping` | Method | Maps HTTP PUT requests. |
| `@DeleteMapping` | Method | Maps HTTP DELETE requests. |
| `@PathVariable` | Param | Binds route parameters (e.g. `/api/users/{id}`) to controller method parameters. |

---

## 6. Debugging & Logging

| Annotation | Target | Description |
| --- | --- | --- |
| `@EnableDebug` | Type | Enables detailed debug logs for the annotated component. |
| `@EnableDebugFor` | Type | Enables detailed debug logs for specific target classes. |
