# VInject

A lightweight, powerful dependency injection (DI) and configuration framework designed for Java applications and Minecraft plugins. It simplifies dependency management, automates database entity mapping, manages YAML configurations, and offers embedded HTTP server support.

---

## Table of Contents

For detailed tutorials and references, check out the specialized guides:

* **[Introduction & DI Concepts](docs/introduction.md)**: DI basics, Component vs. Service, writing Beans, and framework-managed class constraints.
* **[Features & Annotation Catalog](docs/features.md)**: A complete reference glossary of all VInject annotations and options.
* **[Database & Repositories](docs/database_and_repositories.md)**: CRUD repositories, entity mapping, and schema constraints (no primitives!).
* **[YAML Configuration Mapping](docs/yaml_configuration.md)**: Mapping single files/directories to Java configuration classes.
* **[Testing with VInject](docs/testing.md)**: Isolated unit testing and full-context integration testing.
* **[Caching & Debugging](docs/caching_and_debugging.md)**: Caching strategies (LRU, HOT_AWARE) and granular debug logging.

---

## Getting Started

### 1. Add Dependency to Maven
Add the following repository and dependency to your `pom.xml`:

```xml
<repository>
    <id>vortex-repo</id>
    <url>https://repo.vortexdevelopment.net/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>net.vortexdevelopment</groupId>
    <artifactId>VInject-Core</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

### 2. Configure Bytecode Transformer (Required)
The `vinject-maven-plugin` is required for entity dirty tracking (`@CachedField`), configuration batch loading (`@YamlId`), and compile-time dependency analysis validation.

```xml
<plugin>
    <groupId>net.vortexdevelopment</groupId>
    <artifactId>vinject-maven-plugin</artifactId>
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

---

## Quick Start Example

### 1. Define your Main Class
Annotate your main class with `@Root` to trigger scanning:

```java
package org.example.app;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.VInjectApplication;
import net.vortexdevelopment.vinject.di.DependencyContainer;

@Root(packageName = "org.example.app")
public class MyApplication {
    public static void main(String[] args) {
        // Start the application and scan for components
        DependencyContainer container = VInjectApplication.run(MyApplication.class, args);
    }
}
```

### 2. Create a Component and Service
Inject dependencies automatically using `@Inject`:

```java
package org.example.app.services;

import net.vortexdevelopment.vinject.annotation.component.Service;

@Service
public class GreetingsService {
    public String getGreeting() {
        return "Hello from VInject!";
    }
}
```

```java
package org.example.app.components;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.Inject;
import org.example.app.services.GreetingsService;

@Component
public class WelcomeHandler {

    @Inject
    private GreetingsService greetingsService;

    public void greet() {
        System.out.println(greetingsService.getGreeting());
    }
}
```

---

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

For third-party dependencies and their licenses, please see the [NOTICE](NOTICE) file.
