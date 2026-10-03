# Introduction to VInject & Dependency Injection

Welcome to VInject! This guide provides an easy-to-understand introduction to Dependency Injection (DI) and explains how VInject helps you write clean, modular, and maintainable Java applications.

---

## 1. What is Dependency Injection (DI)?

In standard Java programming, if `Class A` needs to use `Class B` to perform a task, `Class A` will construct `Class B` directly:

```java
public class UserService {
    private UserRepository userRepository = new UserRepository(); // Manual creation!
}
```

This makes the code tightly coupled. If `UserRepository` changes its constructor (e.g., to require a database connection), every class that instantiates `UserRepository` must be updated. It also makes unit testing difficult because you cannot easily swap `UserRepository` for a mock.

**Dependency Injection** flips this control (known as **Inversion of Control** or **IoC**). Instead of classes creating their own dependencies, the framework creates and manages the instances, then automatically "injects" (hands) them to the classes that need them:

```java
public class UserService {
    @Inject
    private UserRepository userRepository; // Injected by VInject!
}
```

---

## 2. Framework-Managed Classes vs. Manual Instantiation

### The Golden Rule: Never Use the `new` Keyword for Managed Components!
When a class is managed by VInject (e.g., annotated with `@Component`, `@Service`, or `@Repository`), you **must not** construct it yourself using `new`:

```java
// WRONG: Bypasses the dependency container
UserService service = new UserService();
service.doSomething(); // Throws NullPointerException because @Inject fields are null!

// RIGHT: Let VInject give you the instance
UserService service = container.getDependency(UserService.class);
service.doSomething(); // Fully resolved and safe to use!
```

**Why?** When you use `new`, Java creates a raw, unmanaged object. The VInject container has no knowledge of this new object and cannot inject any of its `@Inject` fields or call `@PostConstruct` hooks.

---

## 3. Core Component Types

### A. Components (`@Component`)
General-purpose utility or helper classes managed by VInject.

```java
package org.example.plugin.helpers;

import net.vortexdevelopment.vinject.annotation.component.Component;

@Component
public class StringFormatter {
    public String format(String input) {
        return input.trim().toLowerCase();
    }
}
```

### B. Services (`@Service`)
Classes that contain the core business logic of your application.

```java
package org.example.plugin.services;

import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.Inject;
import org.example.plugin.helpers.StringFormatter;

@Service
public class UserService {

    @Inject
    private StringFormatter formatter;

    public void registerUser(String name) {
        String cleanName = formatter.format(name);
        System.out.println("Registering clean user: " + cleanName);
    }
}
```

### C. What is a Bean (`@Bean`)?
Sometimes you need to register classes that you don't own (such as library classes), or classes that require custom configuration during instantiation. You do this using **Bean Methods** annotated with `@Bean` inside any `@Component` or `@Service`.

A bean method is a factory method that:
1. Instantiates a class.
2. Configures it.
3. Returns the instance to be registered in the dependency container.

```java
package org.example.plugin.config;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.Bean;
import java.net.http.HttpClient;

@Component
public class AppConfig {

    // Registers HttpClient in the dependency container
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }
}
```
You can now inject `HttpClient` into any component like this:
```java
@Component
public class ApiClient {
    @Inject
    private HttpClient httpClient; // Injected automatically!
}
```

---

## 4. How VInject Bootstraps

To start your application, mark your main class with `@Root` and call `VInjectApplication.run()`:

```java
package org.example.plugin;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.VInjectApplication;
import net.vortexdevelopment.vinject.di.DependencyContainer;

@Root(packageName = "org.example.plugin") // Tells VInject where to scan for components
public class MyPlugin {

    public static void main(String[] args) {
        // Start the application and initialize all components
        DependencyContainer container = VInjectApplication.run(MyPlugin.class, args);
    }
}
```

> [!TIP]
> **AI Agent & Developer Tip**: If `packageName` is omitted on `@Root`, VInject will auto-detect it using the package of the root class itself.

---

## 5. Dependency Constraints & Troubleshooting

### Circular Dependencies
If `Class A` injects `Class B` and `Class B` injects `Class A`, you have a circular dependency cycle.
* **Constructor Injection**: VInject **cannot** resolve circular dependencies if both classes use constructor parameters. The application will throw a cycle error during startup.
* **Field Injection**: To break cycles, use `@Inject` on fields instead of constructors. VInject can defer field injection until both objects are instantiated.

---

## 6. Next Steps

- [Components & Injection](components-and-injection.md) - `@Root`, injection modes, auto-registration, `@Qualifier`, registry vs `@Component`
- [Lifecycle & Events](lifecycle-and-events.md) - `@PostConstruct`, `@OnLoad`, `@OnDestroy`, `@OnEvent`
- [Load Order & Extensions](load-order-and-extensions.md) - startup phases, `@Registry`, diagnostic codes
