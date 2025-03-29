# VInject Entity Transformer

The VInject Entity Transformer is a powerful component of the VInject framework that enhances database entities with automatic change tracking capabilities at compile time.

## Overview

The Entity Transformer modifies Java classes annotated with `@Entity` during the compilation process, adding functionality to track field modifications. This eliminates the need for manual tracking code, reducing boilerplate and potential errors.

## Key Benefits

- **Efficient Database Updates**: Only modified fields are included in SQL update queries, significantly improving performance for large entities.
- **Automatic Tracking**: No need to manually track which fields have changed in your entities.
- **Zero Runtime Overhead**: All transformations happen at compile time, ensuring optimal runtime performance.
- **Clean Domain Model**: Keep your entity classes focused on business logic without cluttering them with tracking code.

## How It Works

The transformer automatically adds the following to your `@Entity` classes:

- A private `modifiedFields` set to track changed field names
- Getter and setter methods for all fields with built-in change tracking
- Methods to check which fields have been modified
- A reset method to clear the modification history after persisting changes

## Installation

Add the Entity Transformer to your Maven project:

```xml
<plugin>
    <groupId>net.vortexdevelopment</groupId>
    <artifactId>vinject-transformer</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>transform-classes</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

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