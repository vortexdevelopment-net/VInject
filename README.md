# VInject Framework

A lightweight and powerful dependency injection framework for Java applications, designed to simplify dependency management and improve code organization. Originally created for Minecraft plugin development, it provides seamless integration with the Bukkit/Spigot ecosystem while also supporting standalone Java applications.

## Getting Started

### Adding to Your Project

Add the following to your `pom.xml`:

```xml
<repository>
    <id>vortex-repo</id>
    <url>https://repo.vortexdevelopment.net/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>net.vortexdevelopment</groupId>
    <artifactId>VInject-Framework</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

## Features

### Primarily designed for Minecraft plugin development
 - Native integration with Paper plugins
  - Template dependency support for plugin frameworks
  - Automatic plugin lifecycle management
  - All `@Component` and `@Service` classes can be injected.
  - Example plugin structure with VInject and [VortexCore](https://github.com/VortexDevelopment/VortexCore):
  ```java
  package org.example.plugin;
  
  @Root(
      packageName = "org.example.plugin",
      createInstance = false, //Do not create an instance of this class, plugin loader will handle it
      templateDependencies = {
          //Used by the Intellij Plugin
          @TemplateDependency(groupId = "net.vortexdevelopment", artifactId = "VortexCore", version = "1.0.0-SNAPSHOT")
      }
  )
  public final class MyPlugin extends VortexPlugin {
      @Override
      public void onPreComponentLoad() {
          // Initialize before components are loaded
      }

      @Override
      public void onPluginLoad() {
          // Load plugin-specific resources
          Config.load();
      }

      @Override
      protected void onPluginEnable() {
          // Plugin enable logic
      }

      @Override
      protected void onPluginDisable() {
          // Plugin disable logic
      }
  }
  ```

  ### Extras:
  - **Registering Listeners with ease**
    - Use `@RegisterListener` to register event listeners (From VortexCore)
    - Example:
    ```java
    package org.example.plugin.listeners;
    
    import org.example.plugin.MyPlugin;
    import org.bukkit.event.EventHandler;
    import org.bukkit.event.Listener;
    import org.bukkit.event.player.PlayerJoinEvent;
    import net.vortexdevelopment.vinject.annotations.Inject; 
    import net.vortexdevelopment.vortexcore.vinject.annotation.RegisterListener;
    
    @RegisterListener
    public class MyListener implements Listener {
    
        @Inject
        private MyPlugin myPlugin;

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            myPlugin.getLogger().info(event.getPlayer().getName() + " joined the server!");
        }
    }
    ```
    
- **Create Manager classes with `@Component` or `@Service`**
  - Use `@Component` for general-purpose classes
  - Use `@Service` for classes that provide business logic or services
  - Example:
  ```java
  package org.example.plugin.services;
  
  import net.vortexdevelopment.vinject.annotations.Component;
  
  @Component
  public class MyService {
      public void performAction() {
          // Service logic
      }
  }
  ```

- **Annotation-based Dependency Injection**
  - `@Inject` - Mark fields for dependency injection
  - `@Component` - Mark classes as components
  - `@Service` - Mark classes as services
  - `@Bean` - Define bean methods for dependency creation
  - `@Repository` - Mark classes as repositories
  - `@Root` - Mark the main application class

- **Database Integration**
  - Built-in support for database repositories
  - Automatic entity mapping
  - CRUD operations support

- **Flexible Configuration**
  - YAML-backed configuration with annotations (see [YAML configuration](#yaml-configuration))
  - Package scanning with inclusion/exclusion support
  - Custom annotation handlers
  - Dependency order management

### Basic Usage

1. Mark your main class with `@Root`:

```java
package org.example.app;

@Root(packageName = "org.example.app")
public class YourApplication {
    @Inject
    private YourService yourService;

    private static Database database;
    private static RepositoryContainer repositoryContainer;
    private static DependencyContainer container;
    
    public static void main(String[] args) {
        
        
        // Initialize your application
        int poolSize = 10; // Set your desired pool size
        database = new Database("host", "port", "database", "mysql|mariadb", "username", "password", poolSize);
        
        //Initialize the database connection if needed
        //database.init();
        
        // Initialize the repository container
        repositoryContainer = new RepositoryContainer(database);
        
        // Initialize the dependency container which will load all components
        dependencyContainer = new DependencyContainer(
                YourApplication.class.getAnnotation(Root.class), 
                YourApplication.class,
                null, //It will create a new instance of the class
                database, 
                repositoryContainer
        );
        
        //Inject static fields after components are loaded
        dependencyContainer.injectStatic(app);
        //Inject non-static fields
        dependencyContainer.inject(app);
        
        //Your app fully started
    }
}
```

2. Create a service:

```java
@Service
public class YourService {
    @Inject
    private Database database;
    
    public void doSomething() {
        // Your service logic
    }
}
```

3. Create a component:

```java
@Component
public class YourComponent {
    @Inject
    private YourService yourService;
    
    public void doSomething() {
        yourService.doSomething();
    }
}
```

4. Create a repository:

```java
@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    // Your repository methods
}
```

## Maven Transformer Plugin (Required)

**The VInject-Transformer plugin is required for both database entities and YAML configurations.**

Add the transformer plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>net.vortexdevelopment</groupId>
    <artifactId>VInject-Transformer</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>process-classes</id>
            <phase>process-classes</phase>
            <goals>
                <goal>transform-classes</goal>
            </goals>
        </execution>
        <execution>
            <id>process-test-classes</id>
            <phase>process-test-classes</phase>
            <goals>
                <goal>transform-classes</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### What the Transformer Does

- **For `@Entity` classes**: Adds field modification tracking for efficient database updates
- **For YAML configuration classes**: Adds synthetic fields (`__vinject_yaml_batch_id` and `__vinject_yaml_file`) required for batch loading and saving

**Note**: Classes used in YAML batch loading (classes with fields annotated with `@YamlId`) must be processed by the transformer. Without it, YAML configuration features will not work correctly.

## YAML configuration

VInject maps YAML files into Java objects. Paths in `@YamlConfiguration.file` and `@YamlDirectory.dir` are resolved relative to the JVM working directory unless you call `ConfigurationContainer.setRootDirectory(Path)` or `setRootDirectory(String)` before building the `DependencyContainer`.

For batch item types that use `@YamlId`, keep the VInject-Transformer enabled as described in [Maven Transformer Plugin (Required)](#maven-transformer-plugin-required).

### Single-file configuration (`@YamlConfiguration`)

Annotate one class with `@YamlConfiguration` to bind a single YAML file. Values are written into **fields** directly (setters are not required for loading).

- **`file`**: path to the `.yml` file (relative to the configuration root unless absolute).
- **`path`**: optional base prefix for every field on this class. Each field maps to `path` + `.` + key.
- **`@Key("segment")`**: overrides the key segment for that field. When `path` is set, `@Key` is appended under that base (for example `path = "app"` and `@Key("display-name")` → `app.display-name`).

```java
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;

@YamlConfiguration(file = "config.yml", path = "app")
public class AppConfig {
    
    @Key("port")
    private int port;
    
    @Key("display-name")
    private String name;
}
```

```yaml
app:
  port: 8080
  display-name: "My App"
```

Optional attributes: `autoSave`, `asyncSave`, and `encoding` (default UTF-8).

### Nested sections, maps, and lists

Nested POJO fields and parameterized `Map` / `List` types are filled from nested YAML. Use `ConfigurationSection` as a field type when you want the raw subsection.

To map any `ConfigurationSection` to a new instance outside `@YamlConfiguration`, use `ConfigurationContainer.mapSection(Class<T>, ConfigurationSection)`.

### Layout and comments (`@YamlItem`, `@Comment`, newlines)

- **`@YamlItem`** on a class marks a compact YAML object (a single subtree when saving, with tighter field layout).
- **`@Comment`** on a type or field adds comment lines above that entry when saving.
- **`@NewLineBefore`** and **`@NewLineAfter`** on fields control blank lines when YAML is rendered.

### Directory batch loading (`@YamlDirectory`, `@YamlId`, `@YamlCollection`)

A **holder** class loads many YAML files from one directory into typed items.

```java
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;

import java.util.HashMap;
import java.util.Map;

@Component
@YamlDirectory(dir = "rewards", target = Reward.class)
public class RewardDirectory {
    
    @YamlCollection
    private Map<String, Reward> rewards = new HashMap<>();

    public Map<String, Reward> getRewards() {
        return rewards;
    }
}

@YamlItem
public class Reward {
    
    @YamlId
    private String id;
    
    @Key("amount")
    private int amount;
}
```

**On disk:** under `rewards/`, every `.yml` / `.yaml` file is read. **`recursive`** (default `true`) controls subfolders; **`copyDefaults`** copies matching resources from the JAR when the folder is missing or empty.

**YAML shape when `rootKey` is empty (default):** top-level keys are **item IDs**; each key’s value is a section mapped onto `target`.

```yaml
gold:
  amount: 100
diamond:
  amount: 5
```

When **`rootKey`** is set (for example `rootKey = "items"`), that section is taken first and each key *under* it is an item ID.

**`@YamlId`:** the item’s map key is stored in the annotated `String` field. This is what enables batch save and file tracking together with the transformer.

**Holder collections:** after load, every `Map` or `Collection` field on the holder is filled with the loaded items. **`@YamlCollection`** marks the batch field explicitly. The batch id is `holderClass.getName() + "::" + dir`.

**Mapping:** each `target` class is filled from YAML by field mapping, like `@YamlConfiguration`. Register a **`YamlSerializerBase`** when the type cannot be represented as a simple set of fields (see below).

### Custom serializers (`YamlSerializerBase`, `@YamlSerializer`)

Implement `YamlSerializerBase<T>` with `getTargetType()`, `serialize(T)`, and `deserialize(Map<String, Object>)` to control how a type is read and written.

- **Discovery:** classes annotated with `@YamlSerializer` under your `@Root` scan package are instantiated and registered when `ConfigurationContainer` starts (no-arg or injectable constructor).
- **Manual:** `ConfigurationContainer.registerSerializer(...)` or `YamlSerializerRegistry.registerSerializer(...)`.

```java
import net.vortexdevelopment.vinject.annotation.yaml.YamlSerializer;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;

import java.util.HashMap;
import java.util.Map;

public class Coords {
    private final int x, y;
    public Coords(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
}

@YamlSerializer
public class CoordsSerializer implements YamlSerializerBase<Coords> {
    @Override
    public Class<Coords> getTargetType() {
        return Coords.class;
    }

    @Override
    public Map<String, Object> serialize(Coords c) {
        Map<String, Object> m = new HashMap<>();
        m.put("cx", c.getX());
        m.put("cy", c.getY());
        return m;
    }

    @Override
    public Coords deserialize(Map<String, Object> map) {
        int x = ((Number) map.get("cx")).intValue();
        int y = ((Number) map.get("cy")).intValue();
        return new Coords(x, y);
    }
}
```

Fields of type `Coords` in YAML configs then round-trip through this serializer on load and save.

### Conditional components (`@YamlConditional`)

`@YamlConditional` on a class skips registering that component unless a value in a `@YamlConfiguration` class matches. Example: `configuration = MyConfig.class`, `path = "features.vouchers"`, `value = "true"`. Use **`operator`** when you need a comparison other than equality.

## Performance Optimization

For optimal performance with VInject-Transformer, ensure your entity classes have:
- Getters and setters for all fields, or
- Lombok's `@Data` annotation

Example:
```java
@Data
@Entity
public class User {
    private Long id;
    private String name;
    private String email;
}
```

## Advanced Features

### Custom Annotation Handlers

Create custom annotation handlers by extending `AnnotationHandler`:

```java
@Registry(annotation = CustomAnnotation.class, order = RegistryOrder.COMPONENTS)
public class CustomAnnotationHandler extends AnnotationHandler {
    @Override
    public void handle(Class<?> clazz, Object instance, DependencyContainer container) {
        // Your custom handling logic
    }
}
```

### Package Scanning Configuration

Configure package scanning in your `@Root` annotation:

```java
@Root(
    packageName = "com.your.package",
    ignoredPackages = {"com.your.package.excluded"},
    includedPackages = {"com.your.package.included"}
)
public class YourApplication {
    // Your application code
}
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

For third-party dependencies and their licenses, please see the [NOTICE](NOTICE) file. 