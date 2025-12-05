package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.Injectable;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
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
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads classes annotated with @YamlConfiguration and registers instances
 * into the provided DependencyContainer. Initial population is done via
 * direct field assignment. For auto-save functionality, manually call
 * saveConfig() or saveItemObject() methods.
 * 
 * Note: Classes used in YAML batch loading (with @YamlId fields) must be
 * processed by VInject-Transformer to add required synthetic fields.
 */
@Injectable
public class ConfigurationContainer {
    // Hold last created instance so callers (e.g., shutdown hooks) can trigger saves
    private static volatile ConfigurationContainer INSTANCE;

    // When true, all saves will be forced to synchronous mode
    private static volatile boolean forceSyncSave = false;
    // Root directory for relative config paths. Defaults to current working dir
    private static volatile java.nio.file.Path rootDirectory = java.nio.file.Paths.get(System.getProperty("user.dir"));

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
    private static final String SYNTHETIC_BATCH_FIELD = "__vinject_yaml_batch_id";
    private static final String SYNTHETIC_FILE_FIELD = "__vinject_yaml_file";

    public ConfigurationContainer(DependencyContainer container, Set<Class<?>> yamlConfigClasses) {
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
        };

        this.yaml = new Yaml(new Constructor(this.loaderOptions), this.representer, this.dumperOptions, this.loaderOptions);
        loadConfigs(container, yamlConfigClasses);
        INSTANCE = this;
    }

    /**
     * Set root directory for resolving relative config file paths.
     * Pass null to reset to the JVM working directory.
     */
    public static void setRootDirectory(java.nio.file.Path root) {
        if (root == null) {
            rootDirectory = java.nio.file.Paths.get(System.getProperty("user.dir"));
        } else {
            rootDirectory = root;
        }
    }

    public static void setRootDirectory(String rootPath) {
        if (rootPath == null || rootPath.isEmpty()) {
            setRootDirectory((java.nio.file.Path) null);
        } else {
            setRootDirectory(java.nio.file.Paths.get(rootPath));
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
                java.nio.file.Path resolvedPath = resolvePath(filePath);

                // Create raw instance and populate fields directly (avoid setters)
                Object instance = createRawInstance(cfgClass);
                File file = resolvedPath.toFile();
                if (file.exists()) {
                    try (InputStream in = new FileInputStream(file)) {
                        Object data = yaml.load(in);
                                if (data instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> root = (Map<String, Object>) data;
                                    mapToInstance(root, instance, cfgClass, annotation.path());
                            try {
                                // Only save if merging added keys: compare original map's keys with serialized map
                                Map<String, Object> existing = root;
                                Map<String, Object> serialized = objectToMap(instance, cfgClass, annotation.path());
                                if (!serializedEquals(existing, serialized)) {
                                    saveToFile(instance, cfgClass, resolvedPath.toString(), charset, annotation);
                                }
                            } catch (Exception ignored) {
                                // ignore save failures here; they will surface later if critical
                            }
                        }
                    }
                } else {
                    // Ensure parent directories exist and write default file from instance
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    // Write defaults to disk so file exists for future runs
                        try {
                            saveToFile(instance, cfgClass, file.getPath(), charset, annotation);
                        } catch (Exception ignored) {
                        // ignore errors writing defaults; they will surface later
                    }
                }

                // Register proxy in dependency container so it can be injected
                container.addBean(cfgClass, instance);
                // Store proxy as the delegate for saving/inspection (store original class too)
                configs.put(cfgClass, new ConfigEntry(cfgClass, filePath, charset, annotation));
            } catch (Exception e) {
                throw new RuntimeException("Unable to load YAML configuration for class: " + cfgClass.getName(), e);
            }
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

    private Object getValueByPath(Map<String, Object> root, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            @SuppressWarnings("unchecked")
            Map<String, Object> curMap = (Map<String, Object>) current;
            if (!curMap.containsKey(part)) return null;
            current = curMap.get(part);
        }
        return current;
    }

    private Object convertValue(Object value, Class<?> targetType, Field field) throws Exception {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        // If a custom serializer exists for this target type, use it
        YamlSerializerBase<?> ser = serializers.get(targetType);
        if (ser != null) {
            @SuppressWarnings("unchecked")
            YamlSerializerBase<Object> s = (YamlSerializerBase<Object>) ser;
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) value;
                return s.deserialize(m);
            }
        }
        // Handle enum types - convert string to enum constant using .name()
        if (targetType.isEnum()) {
            String enumName = value.toString();
            try {
                // Use reflection to call valueOf method on the enum class
                java.lang.reflect.Method valueOfMethod = targetType.getMethod("valueOf", String.class);
                return valueOfMethod.invoke(null, enumName);
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                throw new RuntimeException("Invalid enum value '" + enumName + "' for enum type " + targetType.getName() + 
                        (field != null ? " (field: " + field.getName() + ")" : ""), e);
            }
        }
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value.toString());
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value.toString());
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value.toString());
        if (List.class.isAssignableFrom(targetType)) {
            if (value instanceof List) return value;
            return Collections.singletonList(value);
        }
        // Handle Map types - convert YAML map to target Map type with proper value conversion
        if (Map.class.isAssignableFrom(targetType) && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = (Map<String, Object>) value;
            
            // Get generic type parameters from field if available
            Class<?> keyType = String.class; // Default to String for keys
            Class<?> valueType = Object.class; // Default to Object for values
            
            if (field != null) {
                java.lang.reflect.Type genericType = field.getGenericType();
                if (genericType instanceof java.lang.reflect.ParameterizedType) {
                    java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                    java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
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
                @SuppressWarnings("unchecked")
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
                @SuppressWarnings("unchecked")
                YamlSerializerBase<Object> s2 = (YamlSerializerBase<Object>) ser2;
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                nested = s2.deserialize(map);
                return nested;
            }
            Object nestedObj = createRawInstance(targetType);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            mapToInstance(map, nestedObj, targetType, "");
            return nestedObj;
        }
        // Fallback: try to convert via string
        return value;
    }


    private Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> cur = clazz;
        while (cur != null && !cur.equals(Object.class)) {
            try {
                Field f = cur.getDeclaredField(name);
                return f;
            } catch (NoSuchFieldException ignored) {}
            cur = cur.getSuperclass();
        }
        return null;
    }

    private java.nio.file.Path resolvePath(String filePath) {
        java.nio.file.Path p = java.nio.file.Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return rootDirectory.resolve(p).normalize();
    }

    private void saveToFile(Object instance, Class<?> inspectedClass, String filePath, Charset charset, YamlConfiguration annotation) {
        try {
            Map<String, Object> dump = objectToMap(instance, inspectedClass, annotation.path());
            File file = new File(filePath);
            file.getParentFile(); // no-op to avoid warnings

            if (file.exists()) {
                // Compose existing document into Node tree so we can update only schema keys
                try (InputStreamReader ir = new InputStreamReader(new FileInputStream(file), charset)) {
                    Node root = yaml.compose(ir);
                    MappingNode mapping;
                    if (root == null) {
                        mapping = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
                    } else if (root instanceof MappingNode mn) {
                        mapping = mn;
                    } else {
                        // Not a mapping document - fall back to full dump
                        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                            // Write class-level comments (if any) above the YAML document
                            try {
                                java.lang.annotation.Annotation a = inspectedClass.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.Comment.class);
                                if (a instanceof net.vortexdevelopment.vinject.annotation.yaml.Comment) {
                                    net.vortexdevelopment.vinject.annotation.yaml.Comment classComment = (net.vortexdevelopment.vinject.annotation.yaml.Comment) a;
                                    String[] lines = classComment.value();
                                    if (lines != null) {
                                        for (String cl : lines) {
                                            if (cl == null) continue;
                                            writer.write("# ");
                                            writer.write(cl);
                                            writer.write(System.lineSeparator());
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {
                                // Non-fatal
                            }
                            yaml.dump(dump, writer);
                        }
                        return;
                    }

                    try {
                        updateMappingNodeWithMap(mapping, dump, this.representer, instance, inspectedClass, annotation.path());
                    } catch (Throwable t) {
                        // On failure, log the error and fall back to full dump
                        System.err.println("Warning: Failed to update YAML node tree, falling back to full dump. This may lose comments and unknown keys.");
                        System.err.println("Error: " + t.getMessage());
                        t.printStackTrace();
                        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                            try {
                                java.lang.annotation.Annotation a = inspectedClass.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.Comment.class);
                                if (a instanceof net.vortexdevelopment.vinject.annotation.yaml.Comment) {
                                    net.vortexdevelopment.vinject.annotation.yaml.Comment classComment = (net.vortexdevelopment.vinject.annotation.yaml.Comment) a;
                                    String[] lines = classComment.value();
                                    if (lines != null) {
                                        for (String cl : lines) {
                                            if (cl == null) continue;
                                            writer.write("# ");
                                            writer.write(cl);
                                            writer.write(System.lineSeparator());
                                        }
                                    }
                                }
                            } catch (Throwable ignore) {}
                            yaml.dump(dump, writer);
                        }
                        return;
                    }

                    // Serialize updated node (preserves comments and unknown keys)
                    try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                        yaml.serialize(mapping, writer);
                    }
                }
                return;
            }

            // File doesn't exist: create new Node tree with comments and serialize
            MappingNode mapping = new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
            try {
                // Use updateMappingNodeWithMap to populate the mapping with data and comments
                updateMappingNodeWithMap(mapping, dump, this.representer, instance, inspectedClass, annotation.path());
                
                // Write class-level comments (if any) above the YAML document
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                    try {
                        java.lang.annotation.Annotation a = inspectedClass.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.Comment.class);
                        if (a instanceof net.vortexdevelopment.vinject.annotation.yaml.Comment) {
                            net.vortexdevelopment.vinject.annotation.yaml.Comment classComment = (net.vortexdevelopment.vinject.annotation.yaml.Comment) a;
                            String[] lines = classComment.value();
                            if (lines != null) {
                                for (String cl : lines) {
                                    if (cl == null) continue;
                                    writer.write("# ");
                                    writer.write(cl);
                                    writer.write(System.lineSeparator());
                                }
                            }
                        }
                    } catch (Throwable ignore) {
                        // Non-fatal; continue
                    }
                    
                    // Serialize the node tree (preserves comments)
                    yaml.serialize(mapping, writer);
                }
            } catch (Throwable t) {
                // On failure, fall back to full dump
                System.err.println("Warning: Failed to create YAML node tree, falling back to full dump. Comments may be lost.");
                System.err.println("Error: " + t.getMessage());
                t.printStackTrace();
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                    try {
                        java.lang.annotation.Annotation a = inspectedClass.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.Comment.class);
                        if (a instanceof net.vortexdevelopment.vinject.annotation.yaml.Comment) {
                            net.vortexdevelopment.vinject.annotation.yaml.Comment classComment = (net.vortexdevelopment.vinject.annotation.yaml.Comment) a;
                            String[] lines = classComment.value();
                            if (lines != null) {
                                for (String cl : lines) {
                                    if (cl == null) continue;
                                    writer.write("# ");
                                    writer.write(cl);
                                    writer.write(System.lineSeparator());
                                }
                            }
                        }
                    } catch (Throwable ignore) {
                        // Non-fatal; continue
                    }
                    yaml.dump(dump, writer);
                }
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
                saveToFile(entry, entry.configClass, entry.filePath, entry.charset, entry.annotation);
            } catch (Exception e) {
                System.err.println("Error saving configuration for " + entry.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateMappingNodeWithMap(MappingNode mapping, Map<String, Object> map, Representer representer, 
                                          Object instance, Class<?> inspectedClass, String basePath) {
        List<NodeTuple> tuples = mapping.getValue();
        
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
        // Also build a map for nested keys: for "App.Welcome Message", map "App" -> field
        Map<String, Field> topLevelKeyToField = new LinkedHashMap<>();
        if (instance != null && inspectedClass != null) {
            Class<?> clazz = inspectedClass;
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String key = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value() : field.getName();
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
        
        // Process keys in field order (for both top-level and nested keys) to maintain order
        List<String> orderedKeys = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();
        
        // First, add keys in field order based on the order they appear in the class
        if (inspectedClass != null) {
            for (Field field : inspectedClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String key = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value() : field.getName();
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
                    // Nested context: extract the part after basePath (e.g., "Welcome Message" from "App.Welcome Message" when basePath is "App")
                    if (fullKey.startsWith(basePath + ".")) {
                        mapKey = fullKey.substring(basePath.length() + 1);
                    } else if (fullKey.equals(basePath)) {
                        // This is the basePath itself
                        mapKey = basePath;
                    }
                }
                
                if (mapKey != null && map.containsKey(mapKey) && !processedKeys.contains(mapKey)) {
                    orderedKeys.add(mapKey);
                    processedKeys.add(mapKey);
                }
            }
        }
        
        // Then add any remaining keys from map that weren't in field order
        for (String key : map.keySet()) {
            if (!processedKeys.contains(key)) {
                orderedKeys.add(key);
            }
        }
        
        // Rebuild tuples list in the correct order
        List<NodeTuple> newTuples = new ArrayList<>();
        Set<String> processedKeysInOrder = new HashSet<>();
        
        // First, process keys in field order
        for (String key : orderedKeys) {
            Object val = map.get(key);
            NodeTuple existingTuple = existingTuples.get(key);
            String fullKey = topLevelKeyToFullKey.get(key);
            Field field = topLevelKeyToField.get(key);
            
            // For nested keys, we need to find the field that matches the nested structure
            // If this is a nested key (like "Welcome Message" within "App"), find the field
            if (field == null && basePath != null && !basePath.isEmpty()) {
                // We're in a nested context, look for fields that match the nested path
                String searchKey = basePath + "." + key;
                field = fullKeyToField.get(searchKey);
            }
            
            // Check if this is a nested parent key (e.g., "App" when field has key "App.Welcome Message")
            // For nested keys like "App.Welcome Message", when processing "App" at top level,
            // we should NOT add comments to "App" - they should be added to "Welcome Message" in the recursive call
            // isNestedParent is true if:
            // 1. We're at the top level (basePath is null/empty)
            // 2. The field's key contains "." and doesn't match the current key
            // If we're in a nested context and found a field, it's the final key, not a parent
            boolean isNestedParent = false;
            if (field != null) {
                String fieldKey = field.isAnnotationPresent(Key.class) ? field.getAnnotation(Key.class).value() : field.getName();
                // Only consider it a nested parent if we're at the top level
                // In nested contexts, if we found a field, it's the final key
                if (basePath == null || basePath.isEmpty()) {
                    isNestedParent = fieldKey.contains(".") && !fieldKey.equals(key);
                }
            } else if (fullKey != null) {
                isNestedParent = fullKey.contains(".") && !fullKey.equals(key);
            }
            
            if (existingTuple != null) {
                // Key exists - update it
                Node kNode = existingTuple.getKeyNode();
                Node vNode = existingTuple.getValueNode();
                
                // Preserve inline comment from existing value node
                // Try to get inline comment using reflection since API may vary
                String inlineComment = null;
                try {
                    java.lang.reflect.Method getInlineMethod = vNode.getClass().getMethod("getInLineComment");
                    Object inlineCommentObj = getInlineMethod.invoke(vNode);
                    if (inlineCommentObj instanceof String) {
                        inlineComment = (String) inlineCommentObj;
                    }
                } catch (Exception ignored) {
                    // Method doesn't exist or failed, ignore
                }
                
                // Add @Comment annotation on key node if present on field AND key doesn't already have comments
                // BUT only if this is the final key (not a nested parent key)
                // For nested keys like "App.Welcome Message", don't add comment to "App" - add it to "Welcome Message"
                if (field != null && field.isAnnotationPresent(net.vortexdevelopment.vinject.annotation.yaml.Comment.class) && !isNestedParent) {
                    // Check if key already has block comments
                    List<CommentLine> existingBlockComments = null;
                    try {
                        java.lang.reflect.Method getBlockCommentsMethod = kNode.getClass().getMethod("getBlockComments");
                        Object existingComments = getBlockCommentsMethod.invoke(kNode);
                        if (existingComments instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<CommentLine> comments = (List<CommentLine>) existingComments;
                            existingBlockComments = comments;
                        }
                    } catch (Exception ignored) {
                        // Method doesn't exist or failed, assume no comments
                    }
                    
                    // Only add comments if key doesn't already have them
                    if (existingBlockComments == null || existingBlockComments.isEmpty()) {
                        net.vortexdevelopment.vinject.annotation.yaml.Comment comment = field.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.Comment.class);
                        String[] lines = comment.value();
                        if (lines != null && lines.length > 0) {
                            List<CommentLine> blockComments = new ArrayList<>();
                            for (String line : lines) {
                                if (line != null && !line.isEmpty()) {
                                    blockComments.add(new CommentLine(null, null, line, CommentType.BLOCK));
                                }
                            }
                            if (!blockComments.isEmpty()) {
                                kNode.setBlockComments(blockComments);
                            }
                        }
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
                        } catch (Exception ignored) {}
                    }
                    // For nested keys like "App.Welcome Message", when processing "App" at top level,
                    // we need to find the field that has a key starting with "App."
                    // Build a new basePath for nested keys
                    String nestedBasePath = null;
                    if (fullKey != null && fullKey.contains(".")) {
                        // This is a nested key like "App.Welcome Message"
                        // When recursively processing, the basePath should be "App" so we can find "App.Welcome Message"
                        nestedBasePath = fullKey.substring(0, fullKey.indexOf('.'));
                    } else if (basePath != null && !basePath.isEmpty()) {
                        // We're already in a nested context, continue with the basePath
                        nestedBasePath = basePath;
                    }
                    // For nested keys, we need to pass the original instance and class so we can find fields
                    // that match the nested path (e.g., "App.Welcome Message")
                    updateMappingNodeWithMap((MappingNode) vNode, sub, representer, 
                                            instance, inspectedClass, nestedBasePath);
                    // Add to new tuples list in order (the nested mapping has been updated in place)
                    newTuples.add(new NodeTuple(kNode, vNode));
                    processedKeysInOrder.add(key);
                } else {
                    // Replace the value node but preserve inline comment
                    Node newVal = representer.represent(val);
                    if (inlineComment != null) {
                        try {
                            java.lang.reflect.Method setInlineMethod = newVal.getClass().getMethod("setInLineComment", String.class);
                            setInlineMethod.invoke(newVal, inlineComment);
                        } catch (Exception ignored) {
                            // Method doesn't exist or failed, ignore
                        }
                    }
                    // Add to new tuples list in order
                    newTuples.add(new NodeTuple(kNode, newVal));
                    processedKeysInOrder.add(key);
                }
            } else {
                // Key doesn't exist - add it
                Node kNode = representer.represent(key);
                Node vNode = representer.represent(val);
                
                // Add @Comment annotation if present on field (new keys don't have comments yet)
                if (field != null && field.isAnnotationPresent(net.vortexdevelopment.vinject.annotation.yaml.Comment.class)) {
                    net.vortexdevelopment.vinject.annotation.yaml.Comment comment = field.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.Comment.class);
                    String[] lines = comment.value();
                    if (lines != null && lines.length > 0) {
                        List<CommentLine> blockComments = new ArrayList<>();
                        for (String line : lines) {
                            if (line != null && !line.isEmpty()) {
                                blockComments.add(new CommentLine(null, null, line, CommentType.BLOCK));
                            }
                        }
                        if (!blockComments.isEmpty()) {
                            kNode.setBlockComments(blockComments);
                        }
                    }
                }
                
                // Add to new tuples list in order
                newTuples.add(new NodeTuple(kNode, vNode));
                processedKeysInOrder.add(key);
            }
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
    }

    public void registerSerializer(YamlSerializerBase<?> serializer) {
        if (serializer == null) return;
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
        if (e != null) e.dirty = true;
    }

    /**
     * Save specific configuration if it has unsaved changes. If force is true, save regardless of dirty flag.
     */
    public void saveConfig(Class<?> configClass, boolean force) {
        ConfigEntry e = configs.get(configClass);
        if (e == null) return;
        synchronized (e) {
            if (!force && !e.dirty) return;
            saveToFile(e, e.configClass, e.filePath, e.charset, e.annotation);
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
     * Reload a configuration from disk, re-reading the YAML file and updating the instance fields.
     * The instance reference is preserved so injected dependencies remain valid.
     * 
     * @param configClass The configuration class to reload
     * @throws IllegalArgumentException if the config class is not found
     * @throws RuntimeException if the YAML file is invalid or cannot be read
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
                    throw new RuntimeException("Configuration instance not found in DependencyContainer for class: " + configClass.getName());
                }

                // Resolve file path
                java.nio.file.Path resolvedPath = resolvePath(entry.filePath);
                File file = resolvedPath.toFile();

                if (!file.exists()) {
                    System.err.println("Warning: Configuration file does not exist: " + resolvedPath + ". Keeping existing configuration.");
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
                        throw new RuntimeException("Invalid YAML format: expected Map, got " + (data != null ? data.getClass().getName() : "null"));
                    }
                }
            } catch (java.io.FileNotFoundException e) {
                System.err.println("Warning: Configuration file not found: " + entry.filePath + ". Keeping existing configuration.");
            } catch (Exception e) {
                throw new RuntimeException("Unable to reload YAML configuration for class: " + configClass.getName(), e);
            }
        }
    }

    /**
     * Reload a configuration by file path. This is a convenience method that finds
     * the config class associated with the given file path and reloads it.
     * 
     * @param filePath The file path of the configuration to reload
     * @throws IllegalArgumentException if no configuration is found for the given file path
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
        for (Class<?> annotated : reflections.getTypesAnnotatedWith(net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory.class)) {
            net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory ann = annotated.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory.class);
            if (ann == null) continue;
            String effectiveId = loadBatchDirectory(ann, annotated);

            // After loading the batch into batchStores, try to populate a @YamlDirectory-annotated holder class
            Map<String, Object> items = batchStores.get(effectiveId);
            if (items == null) items = java.util.Collections.emptyMap();

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
                for (java.lang.reflect.Field field : annotated.getDeclaredFields()) {
                    net.vortexdevelopment.vinject.annotation.yaml.YamlCollection yc = field.getAnnotation(net.vortexdevelopment.vinject.annotation.yaml.YamlCollection.class);
                    String targetId = yc != null && !yc.value().isEmpty() ? yc.value() : effectiveId;
                    if (yc != null) {
                        if (!targetId.equals(effectiveId)) continue;
                        field.setAccessible(true);
                        java.util.List<Object> list = new java.util.ArrayList<>(items.values());
                        field.set(holder, list);
                        populated = true;
                        break;
                    }
                }

                // If no annotated collection field found, try to find a List<T> field matching the target
                if (!populated) {
                    Class<?> targetType = ann.target();
                    for (java.lang.reflect.Field field : annotated.getDeclaredFields()) {
                        if (!java.util.List.class.isAssignableFrom(field.getType())) continue;
                        java.lang.reflect.Type generic = field.getGenericType();
                        if (generic instanceof java.lang.reflect.ParameterizedType pt) {
                            java.lang.reflect.Type arg = pt.getActualTypeArguments()[0];
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

    private String loadBatchDirectory(net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory ann, Class<?> holderClass) {
        String dir = ann.dir();
        // generate effective id as <holderClassFqcn>::<dir>
        String id = holderClass.getName() + "::" + dir;
        Class<?> target = ann.target();
        String rootKey = ann.rootKey();
        boolean recursive = ann.recursive();

        // Serializer must exist for target
        YamlSerializerBase<?> ser = serializers.get(target);
        if (ser == null) {
            throw new RuntimeException("No YamlSerializer registered for target: " + target.getName() + " required by YamlDirectory: " + dir);
        }

        java.nio.file.Path start = resolvePath(dir);
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, ItemMeta> metaMap = new LinkedHashMap<>();
        if (!java.nio.file.Files.exists(start)) return id;

        try {
            java.util.stream.Stream<java.nio.file.Path> stream;
            if (recursive) {
                stream = java.nio.file.Files.walk(start).filter(p -> java.nio.file.Files.isRegularFile(p));
            } else {
                stream = java.nio.file.Files.list(start).filter(p -> java.nio.file.Files.isRegularFile(p));
            }
            stream.filter(p -> {
                String s = p.toString().toLowerCase();
                return s.endsWith(".yml") || s.endsWith(".yaml");
            }).forEach(p -> {
                try (InputStream in = new java.io.FileInputStream(p.toFile())) {
                    Object data = yaml.load(in);
                    if (!(data instanceof Map)) return;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> root = (Map<String, Object>) data;
                    Map<String, Object> source;
                    if (rootKey == null || rootKey.isEmpty()) {
                        source = root;
                    } else {
                        Object val = root.get(rootKey);
                        if (!(val instanceof Map)) return;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sub = (Map<String, Object>) val;
                        source = sub;
                    }
                    for (Map.Entry<String, Object> e : source.entrySet()) {
                        String key = e.getKey();
                        Object v = e.getValue();
                        if (!(v instanceof Map)) continue;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entryMap = (Map<String, Object>) v;
                        @SuppressWarnings("unchecked")
                        YamlSerializerBase<Object> s = (YamlSerializerBase<Object>) ser;
                        Object obj = s.deserialize(entryMap);

                        // require @YamlId present on target class and set it
                        java.lang.reflect.Field idField = findIdFieldForClass(target);
                        if (idField == null) {
                            throw new RuntimeException("Target class " + target.getName() + " must have a field annotated with @YamlId for batch loading");
                        }
                        try {
                            idField.setAccessible(true);
                            idField.set(obj, key);
                        } catch (IllegalAccessException iae) {
                            throw new RuntimeException("Unable to set @YamlId field for class: " + target.getName(), iae);
                        }

                        // instrument item instance to carry meta (batch id and file path)
                        Object instrumented = instrumentItemInstance(obj, id, p.toAbsolutePath().toString());
                        // ensure @YamlId is set on the instrumented instance as well
                        try {
                            idField.setAccessible(true);
                            idField.set(instrumented, key);
                        } catch (IllegalAccessException iae) {
                            throw new RuntimeException("Unable to set @YamlId field for class: " + target.getName(), iae);
                        }
                        items.put(key, instrumented);

                        // record metadata
                        ItemMeta meta = new ItemMeta();
                        meta.filePath = p.toAbsolutePath().toString();
                        meta.layout = (rootKey == null || rootKey.isEmpty()) ? ItemLayout.TOP_LEVEL : ItemLayout.ROOT_KEY;
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

    private enum ItemLayout {TOP_LEVEL, ROOT_KEY, SINGLE_FILE_PER_ITEM}

    private static final class ItemMeta {
        String filePath;
        ItemLayout layout;
        String rootKey;
    }

    private java.lang.reflect.Field findIdFieldForClass(Class<?> clazz) {
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(net.vortexdevelopment.vinject.annotation.yaml.YamlId.class)) return f;
        }
        // check superclasses
        Class<?> sup = clazz.getSuperclass();
        while (sup != null && !sup.equals(Object.class)) {
            for (java.lang.reflect.Field f : sup.getDeclaredFields()) {
                if (f.isAnnotationPresent(net.vortexdevelopment.vinject.annotation.yaml.YamlId.class)) return f;
            }
            sup = sup.getSuperclass();
        }
        return null;
    }

    private YamlSerializerBase<?> findSerializerForInstance(Object instance) {
        if (instance == null) return null;
        Class<?> c = instance.getClass();
        // direct match
        YamlSerializerBase<?> s = serializers.get(c);
        if (s != null) return s;
        // check assignable keys (serializer target is supertype)
        for (Map.Entry<Class<?>, YamlSerializerBase<?>> e : serializers.entrySet()) {
            Class<?> key = e.getKey();
            if (key.isAssignableFrom(c)) return e.getValue();
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
                    System.err.println("Warning: Synthetic batch field " + SYNTHETIC_BATCH_FIELD + 
                            " not found on class " + clazz.getName() + ". " +
                            "Ensure VInject-Transformer is configured in your build.");
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
                    System.err.println("Warning: Synthetic file field " + SYNTHETIC_FILE_FIELD + 
                            " not found on class " + clazz.getName() + ". " +
                            "Ensure VInject-Transformer is configured in your build.");
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

    private String readSyntheticStringField(Object instance, String fieldName) {
        if (instance == null) return null;
        Class<?> clazz = instance.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
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
     * Save a single item by batch id and item id. Updates only the underlying file containing the item.
     */
    public void saveItem(String batchId, String itemId) {
        Map<String, ItemMeta> metaForBatch = batchMeta.get(batchId);
        if (metaForBatch == null) throw new RuntimeException("Batch not found: " + batchId);
        ItemMeta meta = metaForBatch.get(itemId);
        if (meta == null) throw new RuntimeException("Item not found in batch: " + itemId + " for batch " + batchId);

        Map<String, Object> items = batchStores.get(batchId);
        if (items == null) throw new RuntimeException("Batch store not found: " + batchId);
        Object instance = items.get(itemId);
        if (instance == null) throw new RuntimeException("Item instance not found: " + itemId);

        YamlSerializerBase<?> ser = findSerializerForInstance(instance);
        if (ser == null) throw new RuntimeException("No serializer registered for class: " + instance.getClass().getName());

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = ((YamlSerializerBase<Object>) ser).serialize(instance);

        java.nio.file.Path file = java.nio.file.Paths.get(meta.filePath);
        try {
            Object data = null;
            try (InputStream in = new FileInputStream(file.toFile())) {
                data = yaml.load(in);
            }
            Map<String, Object> root;
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) data;
                root = m;
            } else {
                root = new LinkedHashMap<>();
            }

            boolean changed = false;
            if (meta.layout == ItemLayout.TOP_LEVEL) {
                Object before = root.get(itemId);
                root.put(itemId, serialized);
                changed = !serializedEquals(before instanceof Map ? (Map<String,Object>)before : null, serialized);
            } else if (meta.layout == ItemLayout.ROOT_KEY && meta.rootKey != null) {
                Object sub = root.get(meta.rootKey);
                Map<String,Object> subMap;
                if (sub instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> temp = (Map<String,Object>) sub;
                    subMap = temp;
                } else {
                    subMap = new LinkedHashMap<>();
                    root.put(meta.rootKey, subMap);
                }
                Object before = subMap.get(itemId);
                subMap.put(itemId, serialized);
                changed = !serializedEquals(before instanceof Map ? (Map<String,Object>)before : null, serialized);
            } else {
                throw new RuntimeException("Saving for layout " + meta.layout + " is not supported yet");
            }

            if (changed) {
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), metaFileCharset(meta))) {
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
        if (item == null) throw new IllegalArgumentException("item is null");
        java.lang.reflect.Field idField = findIdFieldForClass(item.getClass());
        if (idField == null) throw new RuntimeException("No @YamlId field found on class: " + item.getClass().getName());
        try {
            idField.setAccessible(true);
            Object val = idField.get(item);
            if (val == null) throw new RuntimeException("@YamlId value is null for item: " + item.getClass().getName());
            saveItem(batchId, val.toString());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save an item object by resolving its batch id stored in the synthetic field.
     */
    public void saveItemObject(Object item) {
        if (item == null) throw new IllegalArgumentException("item is null");
        String batchId = readSyntheticStringField(item, SYNTHETIC_BATCH_FIELD);
        if (batchId == null) throw new RuntimeException("No synthetic batch id present on item. Use saveItemObject(batchId, item) or ensure instrumenting is enabled.");
        java.lang.reflect.Field idField = findIdFieldForClass(item.getClass());
        if (idField == null) throw new RuntimeException("No @YamlId field found on class: " + item.getClass().getName());
        try {
            idField.setAccessible(true);
            Object val = idField.get(item);
            if (val == null) throw new RuntimeException("@YamlId value is null for item: " + item.getClass().getName());
            saveItem(batchId, val.toString());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Charset metaFileCharset(ItemMeta meta) {
        for (ConfigEntry e : configs.values()) {
            if (e.filePath.equals(meta.filePath)) return e.charset;
        }
        return Charset.forName("UTF-8");
    }

    

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
            if (ser != null) {
                try {
                    @SuppressWarnings("unchecked")
                    YamlSerializerBase<Object> serObj = (YamlSerializerBase<Object>) ser;
                    Map<String, Object> serialized = serObj.serialize(val);
                    putNested(root, (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key, serialized);
                    continue;
                } catch (Exception e) {
                    // fall through to default handling
                }
            }
            // Handle enum types - serialize as string using .name()
            if (val.getClass().isEnum()) {
                putNested(root, (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key, ((Enum<?>) val).name());
            } else if (val instanceof Map) {
                // Handle Map types - serialize directly as Map
                putNested(root, (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key, val);
            } else if (isPrimitiveOrString(val.getClass())) {
                putNested(root, (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key, val);
            } else if (val instanceof List) {
                putNested(root, (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key, val);
            } else {
                // nested object
                Map<String, Object> nested = objectToMap(val, val.getClass(), "");
                putNested(root, (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key, nested);
            }
        }
        return root;
    }

    // Compare two nested maps for structural equality (used to avoid unnecessary writes)
    private boolean serializedEquals(Map<String, Object> a, Map<String, Object> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (String key : a.keySet()) {
            Object va = a.get(key);
            Object vb = b.get(key);
            if (va instanceof Map && vb instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ma = (Map<String, Object>) va;
                @SuppressWarnings("unchecked")
                Map<String, Object> mb = (Map<String, Object>) vb;
                if (!serializedEquals(ma, mb)) return false;
            } else if (va instanceof List && vb instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> la = (List<Object>) va;
                @SuppressWarnings("unchecked")
                List<Object> lb = (List<Object>) vb;
                if (la.size() != lb.size()) return false;
                for (int i = 0; i < la.size(); i++) {
                    Object ia = la.get(i);
                    Object ib = lb.get(i);
                    if (ia instanceof Map && ib instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ma = (Map<String, Object>) ia;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mb = (Map<String, Object>) ib;
                        if (!serializedEquals(ma, mb)) return false;
                    } else {
                        if (!java.util.Objects.equals(ia, ib)) return false;
                    }
                }
            } else {
                if (!java.util.Objects.equals(va, vb)) return false;
            }
        }
        return true;
    }

    private boolean isPrimitiveOrString(Class<?> clazz) {
        return clazz.isPrimitive() || clazz == String.class || Number.class.isAssignableFrom(clazz) || clazz == Boolean.class || clazz == Character.class;
    }

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
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) next;
                cur = m;
            }
        }
        cur.put(parts[parts.length - 1], value);
    }
}


