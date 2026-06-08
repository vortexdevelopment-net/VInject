# YAML Configuration Mapping

VInject provides a powerful configuration system that maps YAML configuration files directly onto Java objects, removing the need for boilerplate getter/setter loading code.

---

## 1. Single File Configuration (`@YamlConfiguration`)

To map a single YAML file to a Java object, annotate a component class with `@YamlConfiguration`. VInject binds the values directly into the **fields** (setters are not required for loading).

### Simple Example

```java
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;

@YamlConfiguration(file = "config.yml")
public class AppConfig {

    @Key("server-ip")
    private String serverIp = "127.0.0.1"; // Default value if key is missing

    private int port = 25565; // Matches field name if @Key is omitted
}
```

```yaml
server-ip: "play.my-server.com"
port: 25565
```

### Attributes of `@YamlConfiguration`
* **`file`**: Path to the `.yml` / `.yaml` file. Paths are resolved relative to the JVM working directory (unless `ConfigurationContainer.setRootDirectory(...)` is set).
* **`path`**: A base prefix configuration namespace for fields (e.g. `path = "database"`). Fields will map to `database.<fieldname>`.
* **`autoSave`**: Automatically save changes made to config fields back to the disk.
* **`asyncSave`**: Perform saving asynchronously to prevent blocking the main thread (ideal for Minecraft server environments).
* **`encoding`**: Charset encoding (defaults to `UTF-8`).

---

## 2. Nested Sections, Maps, and Lists

VInject supports complex configurations, including nested objects, maps, and lists:

```java
@YamlConfiguration(file = "nested_config.yml")
public class ComplexConfig {

    private DatabaseSettings database; // Maps to a nested subsection
    private List<String> blacklistedPlayers;
    private Map<String, Integer> rewardMultiplier;
}

@YamlItem
public class DatabaseSettings {
    private String host;
    private String username;
    private String password;
}
```

---

## 3. Directory Batch Loading (`@YamlDirectory`)

When you want to load multiple files of the same format from a directory (e.g., individual item configurations, rewards, or custom rank files), use `@YamlDirectory` on a Component class.

```java
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import java.util.HashMap;
import java.util.Map;

@Component
@YamlDirectory(dir = "rewards", target = Reward.class)
public class RewardConfig {

    @YamlCollection
    private Map<String, Reward> rewards = new HashMap<>();

    public Map<String, Reward> getRewards() {
        return rewards;
    }
}

@YamlItem
public class Reward {
    @YamlId
    private String id; // Automatically set to the file name/key name

    private int coins;
}
```

### How Directory Loading Resolves Files
Under `rewards/`, if you have a file `gold.yml`:
```yaml
coins: 100
```
And `diamond.yml`:
```yaml
coins: 500
```
VInject will load these files, instantiate the `Reward` objects, set the `@YamlId` fields (`gold` and `diamond`), and populate the `@YamlCollection` map.

> [!IMPORTANT]
> **Bytecode Requirement**: Any class used as a target in `@YamlDirectory` that has fields annotated with `@YamlId` **must be processed** by the `vinject-maven-plugin`. Without it, directory batch loading and tracking will fail at runtime.

---

## 4. Custom Serializers (`@YamlSerializer`)

If a field uses a class type that cannot be mapped via standard fields (such as a Bukkit `Location` or a custom library coordinate class), you can write a custom serializer.

1. Implement `YamlSerializerBase<T>`.
2. Annotate the serializer class with `@YamlSerializer`.

```java
import net.vortexdevelopment.vinject.annotation.yaml.YamlSerializer;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import java.util.HashMap;
import java.util.Map;

public class Point {
    private final int x, y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
}

@YamlSerializer
public class PointSerializer implements YamlSerializerBase<Point> {
    @Override
    public Class<Point> getTargetType() { return Point.class; }

    @Override
    public Map<String, Object> serialize(Point p) {
        Map<String, Object> map = new HashMap<>();
        map.put("x", p.getX());
        map.put("y", p.getY());
        return map;
    }

    @Override
    public Point deserialize(Map<String, Object> map) {
        int x = ((Number) map.get("x")).intValue();
        int y = ((Number) map.get("y")).intValue();
        return new Point(x, y);
    }
}
```

---

## 5. Layout and Styling

VInject supports styling saved YAML files using annotations to retain readability:

* **`@Comment`**: Adds header comments above fields/classes when saved to disk.
* **`@YamlItem`**: Tells VInject to save the object as a compact structure instead of a loose map.
* **`@NewLineBefore` / `@NewLineAfter`**: Controls blank lines when generating output configurations.

```java
@YamlConfiguration(file = "styled_config.yml")
public class StyledConfig {

    @Comment({"The port the server will bind to.", "Default: 8080"})
    @NewLineAfter
    private int port = 8080;

    @Comment("List of allowed hostnames.")
    private List<String> allowedHosts;
}
```
