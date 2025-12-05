# VInject Entity Transformer

The VInject Entity Transformer is a powerful component of the VInject framework that enhances database entities and YAML configuration classes at compile time.

## Overview

The Entity Transformer modifies Java classes during the compilation process:
- **`@Entity` classes**: Adds automatic field modification tracking for efficient database updates
- **YAML configuration classes**: Adds synthetic fields required for batch loading and saving YAML configurations

This eliminates the need for manual tracking code and runtime bytecode manipulation, reducing boilerplate and improving performance.

## Key Benefits

- **Efficient Database Updates**: Only modified fields are included in SQL update queries, significantly improving performance for large entities.
- **Automatic Tracking**: No need to manually track which fields have changed in your entities.
- **Zero Runtime Overhead**: All transformations happen at compile time, ensuring optimal runtime performance.
- **Clean Domain Model**: Keep your entity classes focused on business logic without cluttering them with tracking code.

## How It Works

### For `@Entity` Classes

The transformer automatically adds the following to your `@Entity` classes:

- A private `modifiedFields` set to track changed field names
- Getter and setter methods for all fields with built-in change tracking
- Methods to check which fields have been modified
- A reset method to clear the modification history after persisting changes

### For YAML Configuration Classes

The transformer automatically adds the following to classes that have fields annotated with `@YamlId`:

- A private `__vinject_yaml_batch_id` field to store the batch identifier
- A private `__vinject_yaml_file` field to store the source file path

These fields are required for the `ConfigurationContainer` to track and save YAML configuration items correctly.

## Installation

Add the Entity Transformer to your Maven project:

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

**Important**: The transformer is required for both database entities and YAML configurations. Without it, YAML batch loading features will not work correctly.

## Integration

The Entity Transformer works seamlessly with VInject's database module, which automatically uses the tracked fields information to generate optimized SQL queries that only update the modified fields, improving performance and reducing database load.

## License

VInject Entity Transformer is available under the Apache License 2.0. See the [LICENSE](../LICENSE) file for more details.

### Third-party dependencies

This project uses third-party libraries licensed under the Apache License 2.0:

- Apache Maven Plugin API
- Apache Maven Plugin Tools Annotations
- Apache Maven Core
- Apache Commons BCEL

See the [NOTICE](NOTICE) file for more details.