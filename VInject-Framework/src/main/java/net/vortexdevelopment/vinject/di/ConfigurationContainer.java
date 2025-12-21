package net.vortexdevelopment.vinject.di;

import lombok.val;
import net.vortexdevelopment.vinject.annotation.Injectable;
import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads classes annotated with @YamlConfiguration and registers instances
 * into the provided DependencyContainer. Initial population is done via
 * direct field assignment. For auto-save functionality, manually call
 * saveConfig() or saveItemObject() methods.
 * <p>
 * Note: Classes used in YAML batch loading (with @YamlId fields) must be
 * processed by VInject-Transformer to add required synthetic fields.
 */
@Injectable
public class ConfigurationContainer {
    // Hold last created instance so callers (e.g., shutdown hooks) can trigger saves
    private static volatile ConfigurationContainer INSTANCE;

    // When true, all saves will be forced to synchronous mode
    private static volatile boolean forceSyncSave = false;
    // Root directory for relative config paths. Defaults to current working dir (normalized and absolute)
    private static volatile Path rootDirectory = Paths.get(System.getProperty("user.dir")).normalize().toAbsolutePath();

    private final DependencyContainer container;
    private final Map<Class<?>, ConfigEntry> configs = new ConcurrentHashMap<>();
    private final Map<Class<?>, YamlSerializerBase<?>> serializers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Yaml yaml;
    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;
    private final Representer representer;
    private final Map<String, Map<String, Object>> batchStores = new ConcurrentHashMap<>();
    // metadata: batchId -> itemId -> Meta
    private final Map<String, Map<String, ItemMeta>> batchMeta = new ConcurrentHashMap<>();
    // file path -> list of itemIds contained
    private final Map<String, List<String>> fileToItemIds = new ConcurrentHashMap<>();
    // Track which config classes write to which file, and which top-level keys they own
    private final Map<String, Map<String, Class<?>>> fileToKeyToClass = new ConcurrentHashMap<>();
    private static final String SYNTHETIC_BATCH_FIELD = "__vinject_yaml_batch_id";
    private static final String SYNTHETIC_FILE_FIELD = "__vinject_yaml_file";

    public ConfigurationContainer(DependencyContainer container, Set<Class<?>> yamlConfigClasses) {
        this.container = container;
        // Configure YAML loader/dumper to preserve comments
        this.dumperOptions = new DumperOptions();
        this.dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.dumperOptions.setPrettyFlow(false);
        this.dumperOptions.setProcessComments(true);

        this.loaderOptions = new LoaderOptions();
        this.loaderOptions.setProcessComments(true);

        // Custom representer: render empty sequences in flow style (i.e., [] inline)
        this.representer = new Representer(this.dumperOptions) {
            @Override
            protected Node representSequence(Tag tag, Iterable<?> sequence, DumperOptions.FlowStyle flowStyle) {
                if (sequence instanceof java.util.Collection<?> coll && coll.isEmpty()) {
                    // Force inline empty sequence
                    return super.representSequence(tag, sequence, DumperOptions.FlowStyle.FLOW);
                }
                // Use block style for non-empty sequences
                return super.representSequence(tag, sequence, DumperOptions.FlowStyle.BLOCK);
            }

            @Override
            public Node represent(Object data) {
                if (data instanceof Enum<?>) {
                    return representScalar(Tag.STR, ((Enum<?>) data).name());
                }
                return super.represent(data);
            }
        };

        this.yaml = new Yaml(new Constructor(this.loaderOptions), this.representer, this.dumperOptions, this.loaderOptions);
        loadConfigs(container, yamlConfigClasses);
        INSTANCE = this;
    }

    /**
     * Set root directory for resolving relative config file paths.
     * Pass null to reset to the JVM working directory.
     * The path will be normalized and made absolute if it's relative.
     */
    public static void setRootDirectory(Path root) {
        if (root == null) {
            rootDirectory = Paths.get(System.getProperty("user.dir")).normalize().toAbsolutePath();
        } else {
            // Normalize the path and make it absolute to avoid path resolution issues
            rootDirectory = root.normalize().toAbsolutePath();
        }
    }

    public static void setRootDirectory(String rootPath) {
        if (rootPath == null || rootPath.isEmpty()) {
            setRootDirectory((Path) null);
        } else {
            setRootDirectory(Paths.get(rootPath));
        }
    }

    /**
     * Set whether saves should be forced synchronous (useful during shutdown).
     * This is a global flag and affects subsequent save operations.
     */
    public static void setForceSyncSave(boolean force) {
        forceSyncSave = force;
    }

    public static boolean isForceSyncSave() {
        return forceSyncSave;
    }

    /**
     * Get the last created ConfigurationContainer instance (if any).
     */
    public static ConfigurationContainer getInstance() {
        return INSTANCE;
    }

    private static final class ConfigEntry {
        final Class<?> configClass;
        final String filePath;
        final Charset charset;
        final YamlConfiguration annotation;
        // whether instance has unsaved changes
        volatile boolean dirty;

        ConfigEntry(Class<?> configClass, String filePath, Charset charset, YamlConfiguration annotation) {
            this.configClass = configClass;
            this.filePath = filePath;
            this.charset = charset;
            this.annotation = annotation;
            this.dirty = false;
        }
    }

    private void loadConfigs(DependencyContainer container, Set<Class<?>> yamlConfigClasses) {
        for (Class<?> cfgClass : yamlConfigClasses) {
            try {
                YamlConfiguration annotation = cfgClass.getAnnotation(YamlConfiguration.class);
                if (annotation == null) continue;

                String filePath = annotation.file();
                Charset charset = Charset.forName(annotation.encoding());

                // Resolve file path relative to rootDirectory when necessary
                Path resolvedPath = resolvePath(filePath);

                // Create raw instance and populate fields directly (avoid setters)
                Object instance = createRawInstance(cfgClass);
                File file = resolvedPath.toFile();
                if (file.exists() && !isFileOnlyWhitespace(file, charset)) {
                    try (InputStream in = new FileInputStream(file)) {
                        Object data = yaml.load(in);
                        if (data instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> root = (Map<String, Object>) data;
                            mapToInstance(root, instance, cfgClass, annotation.path());
                            try {
                                // Only save if merging added keys: compare original map's keys with serialized
                                // map
                                Map<String, Object> existing = root;
                                Map<String, Object> serialized = objectToMap(instance, cfgClass, annotation.path());
                                if (!serializedEquals(existing, serialized)) {
                                    // Pass original file path, not resolved path, so saveToFile can resolve it correctly
                                    saveToFile(instance, cfgClass, filePath, charset, annotation);
                                }
                            } catch (Exception ignored) {
                                // ignore save failures here; they will surface later if critical
                            }
                        }
                    }
                } else {
                    // File doesn't exist or contains only whitespace - write default config
                    // Ensure parent directories exist and write default file from instance
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    // Write defaults to disk so file exists for future runs
                    // Pass original file path, not resolved path, so saveToFile can resolve it correctly
                    try {
                        saveToFile(instance, cfgClass, filePath, charset, annotation);
                    } catch (Exception ignored) {
                        // ignore errors writing defaults; they will surface later
                    }
                }

                // Register proxy in dependency container so it can be injected
                container.addBean(cfgClass, instance);
                // Store proxy as the delegate for saving/inspection (store original class too)
                configs.put(cfgClass, new ConfigEntry(cfgClass, filePath, charset, annotation));
                
                // Track which keys belong to which class for sorting when multiple classes write to same file
                Map<String, Object> serialized = objectToMap(instance, cfgClass, annotation.path());
                Map<String, Class<?>> keyToClass = fileToKeyToClass.computeIfAbsent(filePath, k -> new ConcurrentHashMap<>());
                for (String topLevelKey : serialized.keySet()) {
                    keyToClass.put(topLevelKey, cfgClass);
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to load YAML configuration for class: " + cfgClass.getName(), e);
            }
        }
    }

    /**
     * Check if a file contains only whitespace (spaces, tabs, newlines, etc.).
     * Returns true if the file is empty or contains only whitespace characters.
     */
    private boolean isFileOnlyWhitespace(File file, Charset charset) {
        if (!file.exists() || file.length() == 0) {
            return true;
        }
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), charset)) {
            int ch;
            while ((ch = reader.read()) != -1) {
                if (!Character.isWhitespace(ch)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            // If we can't read the file, treat it as invalid (not whitespace-only)
            return false;
        }
    }

    private Object createRawInstance(Class<?> clazz) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            // Fall back to allocating with Unsafe if no default ctor present
            // Simple fallback: try allocating without invoking constructors is complex; rethrow for clarity
            throw new RuntimeException("Configuration class must have a default constructor: " + clazz.getName(), e);
        }
    }

    private void mapToInstance(Map<String, Object> root, Object instance, Class<?> clazz, String basePath) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);

            String keyPath = getKeyPath(field, basePath);
            Object value = getValueByPath(root, keyPath);
            if (value == null) continue; // keep default

            Class<?> fieldType = field.getType();
            Object converted = convertValue(value, fieldType, field);
            field.set(instance, converted);
        }
    }

    private String getKeyPath(Field field, String basePath) {
        if (field.isAnnotationPresent(Key.class)) {
            String val = field.getAnnotation(Key.class).value();
            return (basePath == null || basePath.isEmpty()) ? val : basePath + "." + val;
        }
        String key = field.getName();
        return (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key;
    }

    @SuppressWarnings("unchecked")
    private Object getValueByPath(Map<String, Object> root, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            Map<String, Object> curMap = (Map<String, Object>) current;
            if (!curMap.containsKey(part)) return null;
            current = curMap.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(Object value, Class<?> targetType, Field field) throws Exception {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        // If a custom serializer exists for this target type, use it
        YamlSerializerBase<?> ser = serializers.get(targetType);
        if (ser != null) {
            YamlSerializerBase<Object> s = (YamlSerializerBase<Object>) ser;
            if (value instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) value;
                return s.deserialize(m);
            }
        }
        // Handle enum types - convert string to enum constant using .name()
        if (targetType.isEnum()) {
            String enumName = value.toString();
            try {
                // Use reflection to call valueOf method on the enum class
                Method valueOfMethod = targetType.getMethod("valueOf", String.class);
                return valueOfMethod.invoke(null, enumName);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Invalid enum value '" + enumName + "' for enum type " + targetType.getName() + (field != null ? " (field: " + field.getName() + ")" : ""), e);
            }
        }
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value.toString());
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value.toString());
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value.toString());
        if (List.class.isAssignableFrom(targetType)) {
            List<Object> resultList = new ArrayList<>();
            Class<?> elementType = Object.class;
            if (field != null) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType pt) {
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?>) {
                        elementType = (Class<?>) typeArgs[0];
                    }
                }
            }

            if (value instanceof List<?> list) {
                for (Object item : list) {
                    // For enum lists, convert string values to enum constants
                    if (elementType.isEnum()) {
                        String enumName = item.toString();
                        try {
                            Method valueOfMethod = elementType.getMethod("valueOf", String.class);
                            Object enumValue = valueOfMethod.invoke(null, enumName);
                            resultList.add(enumValue);
                        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException("Invalid enum value '" + enumName + "' for enum type " + elementType.getName() + (field != null ? " (field: " + field.getName() + ")" : ""), e);
                        }
                    } else {
                        resultList.add(convertValue(item, elementType, null));
                    }
                }
            } else {
                resultList.add(convertValue(value, elementType, null));
            }
            return resultList;
        }
        // Handle Map types - convert YAML map to target Map type with proper value conversion
        if (Map.class.isAssignableFrom(targetType) && value instanceof Map) {
            Map<String, Object> yamlMap = (Map<String, Object>) value;

            // Get generic type parameters from field if available
            Class<?> keyType = String.class; // Default to String for keys
            Class<?> valueType = Object.class; // Default to Object for values

            if (field != null) {
                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type[] typeArgs = pt.getActualTypeArguments();
                    if (typeArgs.length >= 1) {
                        if (typeArgs[0] instanceof Class<?>) {
                            keyType = (Class<?>) typeArgs[0];
                        }
                    }
                    if (typeArgs.length >= 2) {
                        if (typeArgs[1] instanceof Class<?>) {
                            valueType = (Class<?>) typeArgs[1];
                        }
                    }
                }
            }

            // Create a new map instance of the target type
            Map<Object, Object> resultMap;
            try {
                Map<Object, Object> mapInstance = (Map<Object, Object>) targetType.getDeclaredConstructor().newInstance();
                resultMap = mapInstance;
            } catch (Exception e) {
                // Fallback to HashMap if default constructor not available
                resultMap = new java.util.HashMap<>();
            }

            // Convert each entry, converting keys and values to their target types
            for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
                Object key = entry.getKey();
                // Convert key if needed (usually String, but handle other types)
                if (keyType != String.class && !keyType.isAssignableFrom(key.getClass())) {
                    key = convertValue(key, keyType, null);
                }

                // Convert value to target value type
                Object convertedValue = convertValue(entry.getValue(), valueType, null);
                resultMap.put(key, convertedValue);
            }

            return resultMap;
        }
        // For nested objects, assume map or use serializer
        if (value instanceof Map) {
            Object nested = null;
            YamlSerializerBase<?> ser2 = serializers.get(targetType);
            if (ser2 != null) {
                YamlSerializerBase<Object> s2 = (YamlSerializerBase<Object>) ser2;
                Map<String, Object> map = (Map<String, Object>) value;
                nested = s2.deserialize(map);
                return nested;
            }
            Object nestedObj = createRawInstance(targetType);
            Map<String, Object> map = (Map<String, Object>) value;
            mapToInstance(map, nestedObj, targetType, "");
            return nestedObj;
        }
        // Fallback: try to convert via string
        return value;
    }

    /**
     * Writes class-level comments to the writer.
     * Handles lines that already start with '#' (writes as-is),
     * empty strings (writes blank line), and regular lines (adds '# ' prefix).
     */
    private void writeClassLevelComments(java.io.Writer writer, Class<?> inspectedClass) throws java.io.IOException {
        try {
            java.lang.annotation.Annotation a = inspectedClass
                    .getAnnotation(Comment.class);
            if (a instanceof Comment) {
                Comment classComment = (Comment) a;
                String[] lines = classComment.value();
                if (lines != null) {
                    for (String cl : lines) {
                        if (cl == null)
                            continue;
                        // Trim leading/trailing whitespace for comparison, but preserve original for
                        // output
                        String trimmed = cl.trim();
                        // If line is empty (after trimming), write just a newline (blank line)
                        if (trimmed.isEmpty()) {
                            writer.write(System.lineSeparator());
                        }
                        // If line already starts with '#', write it as-is (preserve original
                        // formatting)
                        else if (trimmed.startsWith("#")) {
                            writer.write(cl);
                            writer.write(System.lineSeparator());
                        }
                        // Otherwise, add '# ' prefix
                        else {
                            writer.write("# ");
                            writer.write(cl);
                            writer.write(System.lineSeparator());
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
            // Non-fatal; continue
        }
    }

    private Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null && !cur.equals(Object.class)) {
            try {
                Field f = cur.getDeclaredField(name);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private Path resolvePath(String filePath) {
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return rootDirectory.resolve(p).normalize();
    }

    private void saveToFile(Object instance, Class<?> inspectedClass, String filePath, Charset charset, YamlConfiguration annotation) {
        try {
            Map<String, Object> dump = objectToMap(instance, inspectedClass, annotation.path());

            // Resolve the file path (handles relative paths)
            Path resolvedPath = resolvePath(filePath);
            File file = resolvedPath.toFile();

            // Check if file exists and has valid YAML content
            Path nioPath = resolvedPath;
            boolean fileHasValidContent = false;
            MappingNode mapping = null;

            if (file.exists() && file.length() > 0 && !isFileOnlyWhitespace(file, charset)) {
                // Try to parse the file (only if it's not whitespace-only)
                try (InputStreamReader ir = new InputStreamReader(new FileInputStream(file), charset)) {
                    Node root = yaml.compose(ir);
                    if (root instanceof MappingNode mn) {
                        mapping = mn;
                        fileHasValidContent = true;
                    } else if (root == null) {
                        // File exists but is empty or contains only whitespace/comments
                        // Treat it as a new file and do a full dump
                        fileHasValidContent = false;
                    }
                } catch (Exception e) {
                    // File exists but parsing failed (invalid YAML, empty, etc.)
                    // Treat it as a new file and do a full dump
                    fileHasValidContent = false;
                }
            }

            if (!fileHasValidContent) {
                // File doesn't exist, is empty, has only whitespace, or has invalid YAML
                // Create a new node tree with all default values, comments, and proper
                // formatting
                try {
                    // Ensure parent directory exists
                    Path parentDir = nioPath.getParent();
                    if (parentDir != null && !Files.exists(parentDir)) {
                        Files.createDirectories(parentDir);
                    }

                    // Create a new mapping node and populate it with data and comments
                    MappingNode newMapping = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
                    updateMappingNodeWithMap(newMapping, dump, this.representer, instance, inspectedClass,
                            annotation.path(), filePath);

                    try (java.io.BufferedWriter writer = Files.newBufferedWriter(nioPath, charset,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        writeClassLevelComments(writer, inspectedClass);
                        yaml.serialize(newMapping, writer);
                        writer.flush();
                    }
                } catch (Exception writeException) {
                    System.err.println("ERROR: Failed to write config file: " + filePath);
                    System.err.println("Error: " + writeException.getMessage());
                    writeException.printStackTrace();
                    throw writeException; // Re-throw to be caught by outer catch
                }
                return;
            }

            // File has valid content - update the mapping
            try {
                boolean hasChanges = updateMappingNodeWithMap(mapping, dump, this.representer, instance, inspectedClass,
                        annotation.path(), filePath);
                // Only save if changes were detected (new keys or comments added)
                if (!hasChanges) {
                    return; // No changes, don't save
                }
            } catch (Throwable t) {
                // On failure, log the error and fall back to full dump
                System.err.println("Warning: Failed to update YAML node tree, falling back to full dump. This may lose comments and unknown keys.");
                System.err.println("Error: " + t.getMessage());
                t.printStackTrace();
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                    // Don't write class-level comments in fallback - file already exists with valid content
                    // The header was already written when the file was first created
                    yaml.dump(dump, writer);
                }
                return;
            }

            // Write phase: serialize updated node (preserves comments and unknown keys)
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                // Never write class-level comments when updating an existing file - they should only be added when creating a new file
                // The file already exists with valid content, so the header was already written when it was first created
                yaml.serialize(mapping, writer);
            }
        } catch (Exception e) {
            System.err.println("Unable to save YAML configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save all loaded configurations synchronously. This will force synchronous
     * write regardless of per-config async settings.
     */
    public void saveAllSync() {
        for (ConfigEntry entry : configs.values()) {
            try {
                // Get the actual instance from the dependency container
                Object instance = container.getDependencyOrNull(entry.configClass);
                if (instance == null) {
                    System.err.println(
                            "ERROR: saveAllSync - Could not get instance for class: " + entry.configClass.getName());
                    continue;
                }
                saveToFile(instance, entry.configClass, entry.filePath, entry.charset, entry.annotation);
            } catch (Exception e) {
                System.err.println(
                        "Error saving configuration for " + entry.configClass.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean updateMappingNodeWithMap(MappingNode mapping, Map<String, Object> map, Representer representer,
                                          Object instance, Class<?> inspectedClass, String basePath, String filePath) {
        boolean hasChanges = false;
        List<NodeTuple> tuples = mapping.getValue();

        // Check if this class is a @YamlItem (compact data object) - skip blank lines
        // for these
        boolean isYamlItem = inspectedClass != null && inspectedClass.isAnnotationPresent(YamlItem.class);

        // First, collect all existing keys in the mapping to preserve them
        Map<String, NodeTuple> existingTuples = new LinkedHashMap<>();
        for (NodeTuple nt : tuples) {
            Node kNode = nt.getKeyNode();
            if (kNode instanceof ScalarNode sk) {
                String key = sk.getValue();
                existingTuples.put(key, nt);
            }
        }

        // Build a map of full key paths to fields for comment lookup and ordering
        // This handles nested keys like "App.Welcome Message"
        Map<String, Field> fullKeyToField = new LinkedHashMap<>();
        Map<String, String> topLevelKeyToFullKey = new LinkedHashMap<>();
        // Also build a map for nested keys: for "App.Welcome Message", map "App" ->
        // field
        Map<String, Field> topLevelKeyToField = new LinkedHashMap<>();
        if (instance != null && inspectedClass != null) {
            Class<?> clazz = inspectedClass;
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                String key = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value()
                        : field.getName();
                // The field's key is already the full path (e.g., "App.Welcome Message")
                // We should use it as-is, not prepend basePath
                String fullKey = key;
                fullKeyToField.put(fullKey, field);
                // Track the top-level key for this field
                String[] parts = fullKey.split("\\.", 2);
                if (parts.length > 0) {
                    // For nested keys like "App.Welcome Message", map "App" to the field
                    // This allows us to find the field when processing the top-level "App" key
                    if (!topLevelKeyToFullKey.containsKey(parts[0]) || parts.length == 1) {
                        topLevelKeyToFullKey.put(parts[0], fullKey);
                        topLevelKeyToField.put(parts[0], field);
                    }
                }
            }
        }

        // Process keys in field order (for both top-level and nested keys) to maintain
        // order. This ensures that even if keys are deleted and re-added, they maintain their
        // proper position based on field order.
        List<String> orderedKeys = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        // First, add keys in field order based on the order they appear in the class
        // This ensures deleted keys are re-added in their correct position
        if (inspectedClass != null) {
            for (Field field : inspectedClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()))
                    continue;
                String key = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value()
                        : field.getName();
                // The field's key is already the full path (e.g., "App.Welcome Message")
                String fullKey = key;

                // Determine which key in the map this field corresponds to
                String mapKey = null;
                if (basePath == null || basePath.isEmpty()) {
                    // Top-level: extract the first part (e.g., "App" from "App.Welcome Message")
                    String[] parts = fullKey.split("\\.", 2);
                    if (parts.length > 0) {
                        mapKey = parts[0];
                    }
                } else {
                    // Nested context: extract the part after basePath (e.g., "Welcome Message" from
                    // "App.Welcome Message" when basePath is "App")
                    if (fullKey.startsWith(basePath + ".")) {
                        mapKey = fullKey.substring(basePath.length() + 1);
                    } else if (fullKey.equals(basePath)) {
                        // This is the basePath itself
                        mapKey = basePath;
                    }
                }

                // Add the key if it exists in the map (including deleted keys that are being re-added)
                if (mapKey != null && map.containsKey(mapKey) && !processedKeys.contains(mapKey)) {
                    orderedKeys.add(mapKey);
                    processedKeys.add(mapKey);
                }
            }
        }

        // Then add any remaining keys from map that weren't in field order
        // Separate keys by whether they belong to the current class or other classes
        // This ensures keys from the current class stay together and maintain relative order
        List<String> remainingKeysFromCurrentClass = new ArrayList<>();
        List<String> remainingKeysFromOtherClasses = new ArrayList<>();
        for (String key : map.keySet()) {
            if (!processedKeys.contains(key)) {
                // Check if this key belongs to the current class being processed
                boolean belongsToCurrentClass = false;
                if (inspectedClass != null && filePath != null) {
                    Map<String, Class<?>> keyToClass = fileToKeyToClass.get(filePath);
                    if (keyToClass != null) {
                        Class<?> keyClass = keyToClass.get(key);
                        belongsToCurrentClass = inspectedClass.equals(keyClass);
                    }
                }
                
                if (belongsToCurrentClass) {
                    // Keys from current class should maintain their relative order
                    // (they'll be sorted alphabetically as fallback)
                    remainingKeysFromCurrentClass.add(key);
                } else {
                    remainingKeysFromOtherClasses.add(key);
                }
            }
        }
        
        // Sort keys from current class alphabetically (maintains some order)
        remainingKeysFromCurrentClass.sort(String::compareTo);
        orderedKeys.addAll(remainingKeysFromCurrentClass);
        
        // Sort keys from other classes by class name if we're at top level and multiple classes write to this file
        if ((basePath == null || basePath.isEmpty()) && filePath != null) {
            Map<String, Class<?>> keyToClass = fileToKeyToClass.get(filePath);
            if (keyToClass != null && !keyToClass.isEmpty()) {
                remainingKeysFromOtherClasses.sort((k1, k2) -> {
                    Class<?> c1 = keyToClass.get(k1);
                    Class<?> c2 = keyToClass.get(k2);
                    if (c1 == null && c2 == null) return k1.compareTo(k2);
                    if (c1 == null) return 1;
                    if (c2 == null) return -1;
                    // Sort by class simple name
                    int classCompare = c1.getSimpleName().compareTo(c2.getSimpleName());
                    if (classCompare != 0) return classCompare;
                    // If same class, sort by key name
                    return k1.compareTo(k2);
                });
            } else {
                // No class mapping, sort alphabetically
                remainingKeysFromOtherClasses.sort(String::compareTo);
            }
        } else {
            // Not top level or no file path, sort alphabetically
            remainingKeysFromOtherClasses.sort(String::compareTo);
        }
        
        orderedKeys.addAll(remainingKeysFromOtherClasses);

        // Rebuild tuples list in the correct order
        List<NodeTuple> newTuples = new ArrayList<>();
        Set<String> processedKeysInOrder = new HashSet<>();
        int keyIndex = 0; // Track position to add blank lines before commented keys (but not the first)

        // First, process keys in field order
        for (String key : orderedKeys) {
            Object val = map.get(key);
            NodeTuple existingTuple = existingTuples.get(key);
            String fullKey = topLevelKeyToFullKey.get(key);
            Field field = topLevelKeyToField.get(key);

            // For nested keys, we need to find the field that matches the nested structure
            // If this is a nested key (like "Welcome Message" within "App"), find the field
            boolean foundNestedField = false;
            if (basePath != null && !basePath.isEmpty()) {
                // We're in a nested context, look for fields that match the nested path
                // First try the exact match: basePath + "." + key
                String searchKey = basePath + "." + key;
                Field nestedField = fullKeyToField.get(searchKey);
                if (nestedField != null) {
                    field = nestedField;
                    String fieldKey = nestedField.isAnnotationPresent(Key.class)
                            ? nestedField.getAnnotation(Key.class).value()
                            : nestedField.getName();
                    fullKey = fieldKey;
                    foundNestedField = true;
                } else {
                    // Fallback: search through all fields to find a match
                    // This handles cases where the key might have different formatting or deeper nesting
                    // (e.g., field key "Modules.Blocks.Enabled" when processing "Blocks" with basePath="Modules")
                    // Prioritize exact matches over prefix matches
                    Field exactMatch = null;
                    Field prefixMatch = null;
                    if (inspectedClass != null) {
                        for (Field f : inspectedClass.getDeclaredFields()) {
                            if (Modifier.isStatic(f.getModifiers()))
                                continue;
                            String fieldKey = f.isAnnotationPresent(Key.class) ? f.getAnnotation(Key.class).value()
                                    : f.getName();
                            // Check for exact match first (leaf key)
                            if (fieldKey.equals(searchKey)) {
                                exactMatch = f;
                                break; // Exact match takes priority
                            } else if (fieldKey.startsWith(searchKey + ".")) {
                                // Prefix match (parent key) - only use if no exact match found
                                if (prefixMatch == null) {
                                    prefixMatch = f;
                                }
                            }
                        }
                    }
                    // Use exact match if found, otherwise use prefix match
                    Field selectedField = exactMatch != null ? exactMatch : prefixMatch;
                    if (selectedField != null) {
                        field = selectedField;
                        String fieldKey = selectedField.isAnnotationPresent(Key.class) ? selectedField.getAnnotation(Key.class).value()
                                : selectedField.getName();
                        fullKey = fieldKey;
                        foundNestedField = true;
                    }
                }
            }

            // Check if this is a nested parent key (e.g., "App" when field has key
            // "App.Welcome Message", or "Blocks" when field has key "Modules.Blocks.Enabled")
            // For nested keys like "App.Welcome Message", when processing "App" at top
            // level,
            // we should NOT add comments to "App" - they should be added to "Welcome
            // Message" in the recursive call
            // isNestedParent is true if:
            // 1. The field's key has more nesting levels beyond the current key
            // 2. At top level: field key contains "." and doesn't match the current key
            // 3. In nested context: field key starts with searchKey + "." (has more levels)
            boolean isNestedParent = false;
            if (field != null) {
                String fieldKey = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value()
                        : field.getName();
                if (basePath != null && !basePath.isEmpty()) {
                    // We're in a nested context - check if field key has more nesting levels
                    String searchKey = basePath + "." + key;
                    // If field key starts with searchKey + ".", it means there are more levels
                    // (e.g., fieldKey="Modules.Blocks.Enabled", searchKey="Modules.Blocks")
                    isNestedParent = fieldKey.startsWith(searchKey + ".");
                } else {
                    // We're at the top level - check if this is a nested parent
                    // It's a nested parent if the field key contains "." and doesn't match the
                    // current key
                    isNestedParent = fieldKey.contains(".") && !fieldKey.equals(key);
                }
            } else if (fullKey != null) {
                if (basePath != null && !basePath.isEmpty()) {
                    String searchKey = basePath + "." + key;
                    isNestedParent = fullKey.startsWith(searchKey + ".");
                } else {
                    isNestedParent = fullKey.contains(".") && !fullKey.equals(key);
                }
            }

            if (existingTuple != null) {
                // Key exists - update it
                Node kNode = existingTuple.getKeyNode();
                Node vNode = existingTuple.getValueNode();

                // Preserve inline comment from existing value node
                // Try to get inline comment using reflection since API may vary
                String inlineComment = null;
                try {
                    Method getInlineMethod = vNode.getClass().getMethod("getInLineComment");
                    Object inlineCommentObj = getInlineMethod.invoke(vNode);
                    if (inlineCommentObj instanceof String) {
                        inlineComment = (String) inlineCommentObj;
                    }
                } catch (Exception ignored) {
                    // MethodValue doesn't exist or failed, ignore
                }

                // Add blank line and @Comment annotation on key node
                // Blank lines are added between field-defined keys to separate config sections
                // but not for dynamic properties from serializers (field == null)
                // and not for @YamlItem classes (compact data objects)
                // Comments are only added to final/leaf keys (not nested parent keys)
                if (field != null && !isYamlItem) {
                    List<CommentLine> blockComments = new ArrayList<>();

                    // Add a blank line before this key if it's not the first key at this level
                    if (keyIndex > 0) {
                        blockComments.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
                    }

                    // Add @Comment annotation lines if present
                    // BUT only if this is the final key (not a nested parent key)
                    if (!isNestedParent && field.isAnnotationPresent(Comment.class)) {
                        Comment comment = field.getAnnotation(Comment.class);
                        if (comment != null) {
                            String[] lines = comment.value();
                            if (lines != null && lines.length > 0) {
                                for (String line : lines) {
                                    if (line != null && !line.isEmpty()) {
                                        // SnakeYAML adds '#' prefix automatically, but we need to ensure there's a
                                        // space after it
                                        // Remove any existing # prefix and trim, then add a leading space
                                        String trimmed = line.trim();
                                        String commentText = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
                                        if (!commentText.isEmpty()) {
                                            // Ensure there's a space after # by prepending a space
                                            blockComments.add(new CommentLine(null, null, " " + commentText, CommentType.BLOCK));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!blockComments.isEmpty()) {
                        kNode.setBlockComments(blockComments);
                    }
                }

                if (val instanceof Map && vNode instanceof MappingNode) {
                    // Recursively update nested mapping, preserving existing keys
                    Map<String, Object> sub = (Map<String, Object>) val;
                    // Get nested instance and class for recursive call
                    Object nestedInstance = null;
                    Class<?> nestedClass = null;
                    if (field != null) {
                        try {
                            field.setAccessible(true);
                            nestedInstance = field.get(instance);
                            if (nestedInstance != null) {
                                nestedClass = nestedInstance.getClass();
                            } else if (!isPrimitiveOrString(field.getType())) {
                                nestedClass = field.getType();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    // Determine whether this is a nested key path or a direct nested object
                    // - Nested key path: @Key("App.Welcome Message") - key is "App", fullKey
                    // contains "."
                    // - Direct nested object: @Key("Settings") - key matches fullKey, no dots
                    String nestedBasePath = null;
                    Object recursiveInstance = instance;
                    Class<?> recursiveClass = inspectedClass;

                    if (fullKey != null && fullKey.contains(".")) {
                        // This is a nested key like "Modules.Blocks.Enabled" or "App.Welcome Message"
                        // When recursively processing, we need to build the basePath correctly
                        if (basePath != null && !basePath.isEmpty()) {
                            // We're already in a nested context, append the current key
                            // e.g., basePath="Modules", key="Blocks" -> nestedBasePath="Modules.Blocks"
                            nestedBasePath = basePath + "." + key;
                        } else {
                            // Top level: extract the first part (e.g., "Modules" from "Modules.Blocks.Enabled")
                            nestedBasePath = fullKey.substring(0, fullKey.indexOf('.'));
                        }
                        // Keep using parent instance/class to find fields with nested key paths
                    } else if (basePath != null && !basePath.isEmpty()) {
                        // We're already in a nested context, continue with the basePath
                        nestedBasePath = basePath;
                    } else if (nestedClass != null && !isNestedParent) {
                        // This is a direct nested object (like Settings)
                        // Use the nested object's class to find its fields
                        recursiveInstance = nestedInstance;
                        recursiveClass = nestedClass;
                        nestedBasePath = null; // Reset basePath for the nested class
                    }

                    // Recursive call with appropriate instance and class
                    boolean nestedHasChanges = updateMappingNodeWithMap((MappingNode) vNode, sub, representer,
                            recursiveInstance, recursiveClass, nestedBasePath, filePath);
                    if (nestedHasChanges) {
                        hasChanges = true;
                    }
                    // Add to new tuples list in order (the nested mapping has been updated in
                    // place)
                    newTuples.add(new NodeTuple(kNode, vNode));
                    processedKeysInOrder.add(key);
                } else {
                    // Replace the value node but preserve inline comment
                    Node newVal = representer.represent(val);
                    if (inlineComment != null) {
                        try {
                            Method setInlineMethod = newVal.getClass().getMethod("setInLineComment", String.class);
                            setInlineMethod.invoke(newVal, inlineComment);
                        } catch (Exception ignored) {
                            // MethodValue doesn't exist or failed, ignore
                        }
                    }
                    // Add to new tuples list in order
                    newTuples.add(new NodeTuple(kNode, newVal));
                    processedKeysInOrder.add(key);
                }
            } else {
                // Key doesn't exist - add it (this is a new key, so we have changes)
                hasChanges = true;
                Node kNode = representer.represent(key);
                // For nested maps, create an empty MappingNode first so we can properly add comments
                // Otherwise, use the representer to create the node
                Node vNode;
                if (val instanceof Map) {
                    vNode = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
                } else {
                    vNode = representer.represent(val);
                }

                // Add blank line and @Comment annotation on key node
                // Blank lines are added between field-defined keys to separate config sections
                // but not for dynamic properties from serializers (field == null)
                // and not for @YamlItem classes (compact data objects)
                // Comments are only added to final/leaf keys (not nested parent keys)
                if (field != null && !isYamlItem) {
                    List<CommentLine> blockComments = new ArrayList<>();

                    // Add a blank line before this key if it's not the first key at this level
                    if (keyIndex > 0) {
                        blockComments.add(new CommentLine(null, null, "", CommentType.BLANK_LINE));
                    }

                    // Add @Comment annotation lines if present
                    // BUT only if this is the final key (not a nested parent key)
                    if (!isNestedParent && field.isAnnotationPresent(Comment.class)) {

                        Comment comment = field.getAnnotation(Comment.class);
                        if (comment != null) {
                            String[] lines = comment.value();
                            if (lines != null && lines.length > 0) {
                                for (String line : lines) {
                                    if (line != null && !line.isEmpty()) {
                                        // SnakeYAML adds '#' prefix automatically, but we need to ensure there's a
                                        // space after it
                                        // Remove any existing # prefix and trim, then add a leading space
                                        String trimmed = line.trim();
                                        String commentText = trimmed.startsWith("#") ? trimmed.substring(1).trim() : trimmed;
                                        if (!commentText.isEmpty()) {
                                            // Ensure there's a space after # by prepending a space
                                            blockComments.add(new CommentLine(null, null, " " + commentText, CommentType.BLOCK));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!blockComments.isEmpty()) {
                        kNode.setBlockComments(blockComments);
                    }
                }

                // If the value is a nested Map, recursively process it to add comments to
                // nested fields
                if (val instanceof Map && vNode instanceof MappingNode) {
                    Map<String, Object> sub = (Map<String, Object>) val;
                    // Get nested instance and class for recursive call
                    Object nestedInstance = null;
                    Class<?> nestedClass = null;
                    if (field != null) {
                        try {
                            field.setAccessible(true);
                            nestedInstance = field.get(instance);
                            if (nestedInstance != null) {
                                nestedClass = nestedInstance.getClass();
                            } else if (!isPrimitiveOrString(field.getType())) {
                                nestedClass = field.getType();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    // Determine whether this is a nested key path or a direct nested object
                    String nestedBasePath = null;
                    Object recursiveInstance = instance;
                    Class<?> recursiveClass = inspectedClass;

                    if (fullKey != null && fullKey.contains(".")) {
                        // This is a nested key like "Modules.Blocks.Enabled" or "App.Welcome Message"
                        // When recursively processing, we need to build the basePath correctly
                        if (basePath != null && !basePath.isEmpty()) {
                            // We're already in a nested context, append the current key
                            // e.g., basePath="Modules", key="Blocks" -> nestedBasePath="Modules.Blocks"
                            nestedBasePath = basePath + "." + key;
                        } else {
                            // Top level: extract the first part (e.g., "Modules" from "Modules.Blocks.Enabled")
                            nestedBasePath = fullKey.substring(0, fullKey.indexOf('.'));
                        }
                    } else if (basePath != null && !basePath.isEmpty()) {
                        nestedBasePath = basePath;
                    } else if (nestedClass != null && !isNestedParent) {
                        // This is a direct nested object (like Settings)
                        // Use the nested object's class to find its fields
                        recursiveInstance = nestedInstance;
                        recursiveClass = nestedClass;
                        nestedBasePath = null; // Reset basePath for the nested class
                    } else {
                        // Top-level key, use it as basePath for nested processing
                        nestedBasePath = key;
                    }

                    // Recursively update the new mapping node with comments
                    boolean nestedHasChanges = updateMappingNodeWithMap((MappingNode) vNode, sub, representer, recursiveInstance, recursiveClass, nestedBasePath, filePath);
                    if (nestedHasChanges) {
                        hasChanges = true;
                    }
                }

                // Add to new tuples list in order
                newTuples.add(new NodeTuple(kNode, vNode));
                processedKeysInOrder.add(key);
            }
            // Increment keyIndex AFTER processing (so next key will have blank line if
            // needed)
            keyIndex++;
        }

        // Add any existing keys that weren't in the map (preserve unknown keys)
        for (NodeTuple nt : tuples) {
            Node kNode = nt.getKeyNode();
            if (kNode instanceof ScalarNode sk) {
                String key = sk.getValue();
                if (!processedKeysInOrder.contains(key)) {
                    newTuples.add(nt);
                }
            }
        }

        // Replace the old tuples list with the new ordered one
        tuples.clear();
        tuples.addAll(newTuples);
        
        return hasChanges;
    }

    public void registerSerializer(YamlSerializerBase<?> serializer) {
        if (serializer == null)
            return;
        Class<?> target = serializer.getTargetType();
        if (target != null) {
            serializers.put(target, serializer);
        }
    }

    /**
     * Mark a configuration as dirty (unsaved changes) by class.
     */
    public void markDirty(Class<?> configClass) {
        ConfigEntry e = configs.get(configClass);
        if (e != null)
            e.dirty = true;
    }

    /**
     * Save specific configuration if it has unsaved changes. If force is true, save
     * regardless of dirty flag.
     */
    public void saveConfig(Class<?> configClass, boolean force) {
        ConfigEntry e = configs.get(configClass);
        if (e == null) {
            return;
        }
        synchronized (e) {
            // Check if file is empty or doesn't exist - if so, always save regardless of
            // dirty flag
            Path resolvedPath = resolvePath(e.filePath);
            File file = resolvedPath.toFile();
            boolean fileIsEmpty = !file.exists() || file.length() == 0;

            if (!force && !e.dirty && !fileIsEmpty) {
                return;
            }

            // Get the actual instance from the dependency container
            Object instance = container.getDependencyOrNull(e.configClass);
            if (instance == null) {
                return;
            }

            saveToFile(instance, e.configClass, e.filePath, e.charset, e.annotation);
            e.dirty = false;
        }
    }

    /**
     * Save specific configuration if it has unsaved changes.
     */
    public void saveConfig(Class<?> configClass) {
        saveConfig(configClass, false);
    }

    /**
     * Save specific configuration by file path if it has unsaved changes.
     */
    public void saveConfigByPath(String filePath) {
        for (ConfigEntry e : configs.values()) {
            if (e.filePath.equals(filePath)) {
                saveConfig(e.configClass);
                break;
            }
        }
    }

    /**
     * Reload a configuration from disk, re-reading the YAML file and updating the
     * instance fields.
     * The instance reference is preserved so injected dependencies remain valid.
     *
     * @param configClass The configuration class to reload
     * @throws IllegalArgumentException if the config class is not found
     * @throws RuntimeException         if the YAML file is invalid or cannot be
     *                                  read
     */
    public void reloadConfig(Class<?> configClass) {
        ConfigEntry entry = configs.get(configClass);
        if (entry == null) {
            throw new IllegalArgumentException("Configuration class not found: " + configClass.getName());
        }

        synchronized (entry) {
            try {
                // Get the existing instance from DependencyContainer
                DependencyContainer container = DependencyContainer.getInstance();
                if (container == null) {
                    throw new RuntimeException("DependencyContainer instance not available");
                }

                Object instance = container.getDependencyOrNull(configClass);
                if (instance == null) {
                    throw new RuntimeException("Configuration instance not found in DependencyContainer for class: "
                            + configClass.getName());
                }

                // Resolve file path
                Path resolvedPath = resolvePath(entry.filePath);
                File file = resolvedPath.toFile();

                if (!file.exists()) {
                    System.err.println("Warning: Configuration file does not exist: " + resolvedPath
                            + ". Keeping existing configuration.");
                    return;
                }

                // Re-read YAML file
                try (InputStream in = new FileInputStream(file)) {
                    Object data = yaml.load(in);
                    if (data instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> root = (Map<String, Object>) data;
                        // Update instance fields directly (preserves instance reference)
                        mapToInstance(root, instance, entry.configClass, entry.annotation.path());
                        // Mark as not dirty since we just loaded from disk
                        entry.dirty = false;
                    } else {
                        throw new RuntimeException("Invalid YAML format: expected Map, got "
                                + (data != null ? data.getClass().getName() : "null"));
                    }
                }
            } catch (java.io.FileNotFoundException e) {
                System.err.println("Warning: Configuration file not found: " + entry.filePath
                        + ". Keeping existing configuration.");
            } catch (Exception e) {
                throw new RuntimeException("Unable to reload YAML configuration for class: " + configClass.getName(),
                        e);
            }
        }
    }

    /**
     * Reload a configuration by file path. This is a convenience method that finds
     * the config class associated with the given file path and reloads it.
     *
     * @param filePath The file path of the configuration to reload
     * @throws IllegalArgumentException if no configuration is found for the given
     *                                  file path
     */
    public void reloadConfigByPath(String filePath) {
        ConfigEntry found = null;
        for (ConfigEntry e : configs.values()) {
            if (e.filePath.equals(filePath)) {
                found = e;
                break;
            }
        }

        if (found == null) {
            throw new IllegalArgumentException("No configuration found for file path: " + filePath);
        }

        reloadConfig(found.configClass);
    }

    /**
     * Load all YamlDirectory annotations found in the given Reflections scanner.
     * This will populate internal batch stores keyed by annotation id or dir path.
     */
    public void loadBatches(org.reflections.Reflections reflections, DependencyContainer container) {
        for (Class<?> annotated : reflections
                .getTypesAnnotatedWith(YamlDirectory.class)) {
            YamlDirectory ann = annotated
                    .getAnnotation(YamlDirectory.class);
            if (ann == null)
                continue;
            String effectiveId = loadBatchDirectory(ann, annotated);

            // After loading the batch into batchStores, try to populate a
            // @YamlDirectory-annotated holder class
            Map<String, Object> items = batchStores.get(effectiveId);
            if (items == null)
                items = java.util.Collections.emptyMap();

            try {
                Object holder = null;
                // create instance via DI container to allow injections if available
                if (container != null) {
                    holder = container.newInstance(annotated);
                } else {
                    holder = annotated.getDeclaredConstructor().newInstance();
                }

                // Find fields annotated with @YamlCollection and set them
                boolean populated = false;
                for (Field field : annotated.getDeclaredFields()) {
                    YamlCollection yc = field
                            .getAnnotation(YamlCollection.class);
                    String targetId = yc != null && !yc.value().isEmpty() ? yc.value() : effectiveId;
                    if (yc != null) {
                        if (!targetId.equals(effectiveId))
                            continue;
                        field.setAccessible(true);
                        java.util.List<Object> list = new java.util.ArrayList<>(items.values());
                        field.set(holder, list);
                        populated = true;
                        break;
                    }
                }

                // If no annotated collection field found, try to find a List<T> field matching
                // the target
                if (!populated) {
                    Class<?> targetType = ann.target();
                    for (Field field : annotated.getDeclaredFields()) {
                        if (!java.util.List.class.isAssignableFrom(field.getType()))
                            continue;
                        Type generic = field.getGenericType();
                        if (generic instanceof ParameterizedType pt) {
                            Type arg = pt.getActualTypeArguments()[0];
                            if (arg instanceof Class<?> argClass && argClass.equals(targetType)) {
                                field.setAccessible(true);
                                java.util.List<Object> list = new java.util.ArrayList<>();
                                for (Object v : items.values()) {
                                    list.add(v);
                                }
                                field.set(holder, list);
                                populated = true;
                                break;
                            }
                        }
                    }
                }

                // Register holder in DI container so it can be injected
                if (container != null && holder != null) {
                    container.addBean(annotated, holder);
                }
            } catch (Exception e) {
                System.err.println("Unable to instantiate YamlDirectory holder: " + annotated.getName() + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String loadBatchDirectory(YamlDirectory ann,
                                      Class<?> holderClass) {
        String dir = ann.dir();
        // generate effective id as <holderClassFqcn>::<dir>
        String id = holderClass.getName() + "::" + dir;
        Class<?> target = ann.target();
        String rootKey = ann.rootKey();
        boolean recursive = ann.recursive();

        // Serializer must exist for target
        YamlSerializerBase<?> ser = serializers.get(target);
        if (ser == null) {
            throw new RuntimeException("No YamlSerializer registered for target: " + target.getName()
                    + " required by YamlDirectory: " + dir);
        }

        Path start = resolvePath(dir);
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, ItemMeta> metaMap = new LinkedHashMap<>();
        if (!Files.exists(start))
            return id;

        try {
            java.util.stream.Stream<Path> stream;
            if (recursive) {
                stream = Files.walk(start).filter(p -> Files.isRegularFile(p));
            } else {
                stream = Files.list(start).filter(p -> Files.isRegularFile(p));
            }
            stream.filter(p -> {
                String s = p.toString().toLowerCase();
                return s.endsWith(".yml") || s.endsWith(".yaml");
            }).forEach(p -> {
                try (InputStream in = new java.io.FileInputStream(p.toFile())) {
                    Object data = yaml.load(in);
                    if (!(data instanceof Map))
                        return;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> root = (Map<String, Object>) data;
                    Map<String, Object> source;
                    if (rootKey == null || rootKey.isEmpty()) {
                        source = root;
                    } else {
                        Object val = root.get(rootKey);
                        if (!(val instanceof Map))
                            return;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sub = (Map<String, Object>) val;
                        source = sub;
                    }
                    for (Map.Entry<String, Object> e : source.entrySet()) {
                        String key = e.getKey();
                        Object v = e.getValue();
                        if (!(v instanceof Map))
                            continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entryMap = (Map<String, Object>) v;
                        @SuppressWarnings("unchecked")
                        YamlSerializerBase<Object> s = (YamlSerializerBase<Object>) ser;
                        Object obj = s.deserialize(entryMap);

                        // require @YamlId present on target class and set it
                        Field idField = findIdFieldForClass(target);
                        if (idField == null) {
                            throw new RuntimeException("Target class " + target.getName()
                                    + " must have a field annotated with @YamlId for batch loading");
                        }
                        try {
                            idField.setAccessible(true);
                            idField.set(obj, key);
                        } catch (IllegalAccessException iae) {
                            throw new RuntimeException("Unable to set @YamlId field for class: " + target.getName(),
                                    iae);
                        }

                        // instrument item instance to carry meta (batch id and file path)
                        Object instrumented = instrumentItemInstance(obj, id, p.toAbsolutePath().toString());
                        // ensure @YamlId is set on the instrumented instance as well
                        try {
                            idField.setAccessible(true);
                            idField.set(instrumented, key);
                        } catch (IllegalAccessException iae) {
                            throw new RuntimeException("Unable to set @YamlId field for class: " + target.getName(),
                                    iae);
                        }
                        items.put(key, instrumented);

                        // record metadata
                        ItemMeta meta = new ItemMeta();
                        meta.filePath = p.toAbsolutePath().toString();
                        meta.layout = (rootKey == null || rootKey.isEmpty()) ? ItemLayout.TOP_LEVEL
                                : ItemLayout.ROOT_KEY;
                        meta.rootKey = (rootKey == null || rootKey.isEmpty()) ? null : rootKey;
                        metaMap.put(key, meta);
                        fileToItemIds.computeIfAbsent(meta.filePath, k -> new ArrayList<>()).add(key);
                    }
                } catch (Exception ex) {
                    System.err.println("Error loading batch file: " + p + " - " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
            stream.close();
        } catch (Exception e) {
            throw new RuntimeException("Unable to scan batch directory: " + dir, e);
        }

        batchStores.put(id, items);
        batchMeta.put(id, metaMap);
        return id;
    }

    private enum ItemLayout {
        TOP_LEVEL, ROOT_KEY, SINGLE_FILE_PER_ITEM
    }

    private static final class ItemMeta {
        String filePath;
        ItemLayout layout;
        String rootKey;
    }

    private Field findIdFieldForClass(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(YamlId.class))
                return f;
        }
        // check superclasses
        Class<?> sup = clazz.getSuperclass();
        while (sup != null && !sup.equals(Object.class)) {
            for (Field f : sup.getDeclaredFields()) {
                if (f.isAnnotationPresent(YamlId.class))
                    return f;
            }
            sup = sup.getSuperclass();
        }
        return null;
    }

    private YamlSerializerBase<?> findSerializerForInstance(Object instance) {
        if (instance == null)
            return null;
        Class<?> c = instance.getClass();
        // direct match
        YamlSerializerBase<?> s = serializers.get(c);
        if (s != null)
            return s;
        // check assignable keys (serializer target is supertype)
        for (Map.Entry<Class<?>, YamlSerializerBase<?>> e : serializers.entrySet()) {
            Class<?> key = e.getKey();
            if (key.isAssignableFrom(c))
                return e.getValue();
        }
        return null;
    }

    private Object instrumentItemInstance(Object original, String batchId, String filePath) {
        // Synthetic fields are now added at compile-time by VInject-Transformer
        // Just set them directly on the original instance using reflection
        try {
            Class<?> clazz = original.getClass();

            // Set batch id field
            try {
                Field batchField = findFieldInHierarchy(clazz, SYNTHETIC_BATCH_FIELD);
                if (batchField != null) {
                    batchField.setAccessible(true);
                    batchField.set(original, batchId);
                } else {
                    System.err.println("Warning: Synthetic batch field " + SYNTHETIC_BATCH_FIELD + " not found on class " + clazz.getName() + ". " + "Ensure VInject-Transformer is configured in your build.");
                }
            } catch (Exception ex) {
                System.err.println("Unable to set synthetic batch field: " + SYNTHETIC_BATCH_FIELD + " - " + ex.getMessage());
            }

            // Set file path field
            try {
                Field fileField = findFieldInHierarchy(clazz, SYNTHETIC_FILE_FIELD);
                if (fileField != null) {
                    fileField.setAccessible(true);
                    fileField.set(original, filePath);
                } else {
                    System.err.println("Warning: Synthetic file field " + SYNTHETIC_FILE_FIELD + " not found on class " + clazz.getName() + ". " + "Ensure VInject-Transformer is configured in your build.");
                }
            } catch (Exception ex) {
                System.err.println("Unable to set synthetic file field: " + SYNTHETIC_FILE_FIELD + " - " + ex.getMessage());
            }

            return original;
        } catch (Exception e) {
            // fallback: return original without metadata
            System.err.println("Error instrumenting item instance: " + e.getMessage());
            return original;
        }
    }

    private String readSyntheticStringField(Object instance) {
        if (instance == null)
            return null;
        Class<?> clazz = instance.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            try {
                Field f = clazz.getDeclaredField(ConfigurationContainer.SYNTHETIC_BATCH_FIELD);
                f.setAccessible(true);
                Object v = f.get(instance);
                return v == null ? null : v.toString();
            } catch (NoSuchFieldException nsf) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException iae) {
                return null;
            }
        }
        return null;
    }

    /**
     * Get loaded batch by id (annotation id or dir path). Returns unmodifiable map.
     */
    public Map<String, Object> getBatch(String id) {
        Map<String, Object> m = batchStores.get(id);
        return m == null ? java.util.Collections.emptyMap() : java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Save a single item by batch id and item id. Updates only the underlying file
     * containing the item.
     */
    @SuppressWarnings("unchecked")
    public void saveItem(String batchId, String itemId) {
        Map<String, ItemMeta> metaForBatch = batchMeta.get(batchId);
        if (metaForBatch == null) {
            throw new RuntimeException("Batch not found: " + batchId);
        }
        ItemMeta meta = metaForBatch.get(itemId);
        if (meta == null) {
            throw new RuntimeException("Item not found in batch: " + itemId + " for batch " + batchId);
        }

        Map<String, Object> items = batchStores.get(batchId);
        if (items == null) {
            throw new RuntimeException("Batch store not found: " + batchId);
        }
        Object instance = items.get(itemId);
        if (instance == null) {
            throw new RuntimeException("Item instance not found: " + itemId);
        }

        YamlSerializerBase<?> ser = findSerializerForInstance(instance);
        if (ser == null) {
            throw new RuntimeException("No serializer registered for class: " + instance.getClass().getName());
        }

        Map<String, Object> serialized = ((YamlSerializerBase<Object>) ser).serialize(instance);

        Path file = Paths.get(meta.filePath);
        try {
            Object data = null;
            try (InputStream in = new FileInputStream(file.toFile())) {
                data = yaml.load(in);
            }
            Map<String, Object> root;
            if (data instanceof Map) {
                root = (Map<String, Object>) data;
            } else {
                root = new LinkedHashMap<>();
            }

            boolean changed = false;
            if (meta.layout == ItemLayout.TOP_LEVEL) {
                Object before = root.get(itemId);
                root.put(itemId, serialized);
                changed = !serializedEquals(before instanceof Map ? (Map<String, Object>) before : null, serialized);
            } else if (meta.layout == ItemLayout.ROOT_KEY && meta.rootKey != null) {
                Object sub = root.get(meta.rootKey);
                Map<String, Object> subMap;
                if (sub instanceof Map) {
                    subMap = (Map<String, Object>) sub;
                } else {
                    subMap = new LinkedHashMap<>();
                    root.put(meta.rootKey, subMap);
                }
                Object before = subMap.get(itemId);
                subMap.put(itemId, serialized);
                changed = !serializedEquals(before instanceof Map ? (Map<String, Object>) before : null, serialized);
            } else {
                throw new RuntimeException("Saving for layout " + meta.layout + " is not supported yet");
            }

            if (changed) {
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file.toFile()),
                        metaFileCharset(meta))) {
                    yaml.dump(root, writer);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to save item " + itemId + " in batch " + batchId, e);
        }
    }

    /**
     * Save an item object by resolving its @YamlId field and batch.
     */
    public void saveItemObject(String batchId, Object item) {
        if (item == null) {
            throw new IllegalArgumentException("item is null");
        }
        Field idField = findIdFieldForClass(item.getClass());
        if (idField == null) {
            throw new RuntimeException("No @YamlId field found on class: " + item.getClass().getName());
        }
        try {
            idField.setAccessible(true);
            Object val = idField.get(item);
            if (val == null) {
                throw new RuntimeException("@YamlId value is null for item: " + item.getClass().getName());
            }
            saveItem(batchId, val.toString());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save an item object by resolving its batch id stored in the synthetic field.
     */
    public void saveItemObject(Object item) {
        if (item == null) {
            throw new IllegalArgumentException("item is null");
        }
        String batchId = readSyntheticStringField(item);
        if (batchId == null) {
            throw new RuntimeException("No synthetic batch id present on item. Use saveItemObject(batchId, item) or ensure instrumenting is enabled.");
        }
        Field idField = findIdFieldForClass(item.getClass());
        if (idField == null) {
            throw new RuntimeException("No @YamlId field found on class: " + item.getClass().getName());
        }
        try {
            idField.setAccessible(true);
            Object val = idField.get(item);
            if (val == null) {
                throw new RuntimeException("@YamlId value is null for item: " + item.getClass().getName());
            }
            saveItem(batchId, val.toString());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Charset metaFileCharset(ItemMeta meta) {
        for (ConfigEntry e : configs.values()) {
            if (e.filePath.equals(meta.filePath)) return e.charset;
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Get a configuration value for the given configuration class and YAML path.
     * This is primarily used by conditional annotations to decide whether a component
     * should be loaded based on a value in a YAML configuration bean.
     *
     * @param configClass The configuration class annotated with @YamlConfiguration
     * @param path        The YAML path to read (relative to the configuration root)
     * @return The value at the given path, or null if not found or an error occurs
     */
    @SuppressWarnings("unchecked")
    public Object getConfigValue(Class<?> configClass, String path) {
        if (configClass == null || path == null || path.isEmpty()) {
            return null;
        }
        ConfigEntry entry = configs.get(configClass);
        if (entry == null) {
            return null;
        }
        try {
            DependencyContainer container = DependencyContainer.getInstance();
            if (container == null) {
                return null;
            }
            Object instance = container.getDependencyOrNull(configClass);
            if (instance == null) {
                return null;
            }
            Map<String, Object> root = objectToMap(instance, entry.configClass, entry.annotation.path());
            return getValueByPath(root, path);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectToMap(Object instance, Class<?> inspectedClass, String basePath) throws IllegalAccessException {
        Map<String, Object> root = new LinkedHashMap<>();
        Class<?> clazz = inspectedClass != null ? inspectedClass : instance.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;

            field.setAccessible(true);
            Object val = field.get(instance);
            String key = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value() : field.getName();
            if (val == null) continue;
            // If a custom serializer exists for this field type, use it
            YamlSerializerBase<?> ser = serializers.get(field.getType());

            String path = (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key;
            if (ser != null) {
                try {
                    YamlSerializerBase<Object> serObj = (YamlSerializerBase<Object>) ser;
                    Map<String, Object> serialized = serObj.serialize(val);
                    putNested(root, path, serialized);
                    continue;
                } catch (Exception e) {
                    // fall through to default handling
                }
            }
            // Handle enum types - serialize as string using .name()
            if (val.getClass().isEnum()) {
                putNested(root, path, ((Enum<?>) val).name());
            } else if (val instanceof Map) {
                // Handle Map types - serialize directly as Map
                putNested(root, path, val);
            } else if (isPrimitiveOrString(val)) {
                putNested(root, path, val);
            } else if (val instanceof List) {
                // Check if this is a list of enums - if so, serialize as string list
                List<?> list = (List<?>) val;
                if (field != null) {
                    Type genericType = field.getGenericType();
                    if (genericType instanceof ParameterizedType pt) {
                        Type[] typeArgs = pt.getActualTypeArguments();
                        if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?>) {
                            Class<?> elementType = (Class<?>) typeArgs[0];
                            if (elementType.isEnum()) {
                                // Convert enum list to string list
                                List<String> stringList = new ArrayList<>();
                                for (Object item : list) {
                                    if (item instanceof Enum<?>) {
                                        stringList.add(((Enum<?>) item).name());
                                    } else {
                                        stringList.add(item.toString());
                                    }
                                }
                                putNested(root, path, stringList);
                                continue;
                            }
                        }
                    }
                }
                putNested(root, path, val);
            } else {
                // nested object
                Map<String, Object> nested = objectToMap(val, val.getClass(), "");
                putNested(root, path, nested);
            }
        }
        return root;
    }

    // Compare two nested maps for structural equality (used to avoid unnecessary writes)
    @SuppressWarnings("unchecked")
    private boolean serializedEquals(Map<String, Object> a, Map<String, Object> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (String key : a.keySet()) {
            Object va = a.get(key);
            Object vb = b.get(key);
            if (va instanceof Map && vb instanceof Map) {
                Map<String, Object> ma = (Map<String, Object>) va;
                Map<String, Object> mb = (Map<String, Object>) vb;
                if (!serializedEquals(ma, mb)) return false;
            } else if (va instanceof List && vb instanceof List) {
                List<Object> la = (List<Object>) va;
                List<Object> lb = (List<Object>) vb;
                if (la.size() != lb.size()) return false;
                for (int i = 0; i < la.size(); i++) {
                    Object ia = la.get(i);
                    Object ib = lb.get(i);
                    if (ia instanceof Map && ib instanceof Map) {
                        Map<String, Object> ma = (Map<String, Object>) ia;
                        Map<String, Object> mb = (Map<String, Object>) ib;
                        if (!serializedEquals(ma, mb)) return false;
                    } else {
                        if (!Objects.equals(ia, ib)) return false;
                    }
                }
            } else {
                if (!Objects.equals(va, vb)) return false;
            }
        }
        return true;
    }

    private boolean isPrimitiveOrString(Class<?> clazz) {
        return clazz.isPrimitive() || clazz == String.class || Number.class.isAssignableFrom(clazz) || clazz == Boolean.class || clazz == Character.class;
    }

    private boolean isPrimitiveOrString(Object object) {
        if (object == null) return false;
        Class<?> clazz = object.getClass();
        return clazz.isPrimitive() || clazz == String.class || Number.class.isAssignableFrom(clazz) || clazz == Boolean.class || clazz == Character.class;
    }

    @SuppressWarnings("unchecked")
    private void putNested(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String p = parts[i];
            Object next = cur.get(p);
            if (!(next instanceof Map)) {
                Map<String, Object> m = new LinkedHashMap<>();
                cur.put(p, m);
                cur = m;
            } else {
                cur = (Map<String, Object>) next;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }
}
