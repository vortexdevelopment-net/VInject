# VInject Framework

A lightweight and powerful dependency injection framework for Java applications, designed to simplify dependency management and improve code organization. Originally created for Minecraft plugin development, it provides seamless integration with the Bukkit/Spigot ecosystem while also supporting standalone Java applications.

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
  - Package scanning with inclusion/exclusion support
  - Custom annotation handlers
  - Dependency order management

## Getting Started

### Adding to Your Project

Add the following to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.VortexDevelopment</groupId>
        <artifactId>VInject-Framework</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

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