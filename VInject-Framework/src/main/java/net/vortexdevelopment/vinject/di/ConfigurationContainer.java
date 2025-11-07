package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads classes annotated with @YamlConfiguration and registers proxy instances
 * into the provided DependencyContainer. The proxies intercept setter calls
 * and trigger save (sync/async) according to the annotation, but initial
 * population is done via direct field assignment so setters are not invoked.
 */
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
    private final Map<String, Map<String, Object>> batchStores = new ConcurrentHashMap<>();
    // metadata: batchId -> itemId -> Meta
    private final Map<String, Map<String, ItemMeta>> batchMeta = new ConcurrentHashMap<>();
    // file path -> list of itemIds contained
    private final Map<String, List<String>> fileToItemIds = new ConcurrentHashMap<>();
    private static final String SYNTHETIC_BATCH_FIELD = "__vinject_yaml_batch_id";
    private static final String SYNTHETIC_FILE_FIELD = "__vinject_yaml_file";

    public ConfigurationContainer(DependencyContainer container, Set<Class<?>> yamlConfigClasses) {
        // Configure YAML dumper to use block style for mappings and sequences
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(false);

        // Custom representer: render empty sequences in flow style (i.e., [] inline)
        Representer representer = new Representer(dumperOptions) {
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

        this.yaml = new Yaml(representer, dumperOptions);
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
        final Object proxy;
        final Object delegate;
        final Class<?> configClass;
        final String filePath;
        final Charset charset;
        final YamlConfiguration annotation;
        // whether instance has unsaved changes
        volatile boolean dirty;

        ConfigEntry(Object proxy, Object delegate, Class<?> configClass, String filePath, Charset charset, YamlConfiguration annotation) {
            this.proxy = proxy;
            this.delegate = delegate;
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

                // Create ByteBuddy-based proxy that delegates behavior to proxy instance
                Object proxy = createProxy(cfgClass, instance, resolvedPath.toString(), annotation, charset, container);

                // Register proxy in dependency container so it can be injected
                container.addBean(cfgClass, proxy);
                // Store proxy as the delegate for saving/inspection (store original class too)
                configs.put(cfgClass, new ConfigEntry(proxy, proxy, cfgClass, filePath, charset, annotation));
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
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(value.toString());
        if (targetType == long.class || targetType == Long.class) return Long.parseLong(value.toString());
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value.toString());
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value.toString());
        if (List.class.isAssignableFrom(targetType)) {
            if (value instanceof List) return value;
            return Collections.singletonList(value);
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

    private Object createProxy(Class<?> clazz, Object instance, String filePath, YamlConfiguration annotation, Charset charset, DependencyContainer container) throws Exception {
        final Interceptor interceptor = new Interceptor(this, clazz, filePath, charset, annotation);

        Class<?> proxyClass = new ByteBuddy()
                .subclass(clazz)
                .method(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)))
                .intercept(MethodDelegation.to(interceptor))
                .make()
                .load(clazz.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        Object proxy = proxyClass.getDeclaredConstructor().newInstance();

        // Copy populated fields from the temporary instance into the proxy so getters/readers see values
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object val = f.get(instance);
            f.set(proxy, val);
        }

        return proxy;
    }

    public static class Interceptor {
        private final ConfigurationContainer container;
        private final Class<?> targetClass;
        private final String filePath;
        private final Charset charset;
        private final YamlConfiguration annotation;

        Interceptor(ConfigurationContainer container, Class<?> targetClass, String filePath, Charset charset, YamlConfiguration annotation) {
            this.container = container;
            this.targetClass = targetClass;
            this.filePath = filePath;
            this.charset = charset;
            this.annotation = annotation;
        }

        @RuntimeType
        public Object intercept(@This Object proxy, @AllArguments Object[] args, @Origin Method method, @SuperCall java.util.concurrent.Callable<?> zuper) throws Throwable {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length == 1) {
                    String fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    try {
                    Field f = container.findFieldInHierarchy(proxy.getClass(), fieldName);
                        if (f != null) {
                            f.setAccessible(true);
                        f.set(proxy, args[0]);
                        } else {
                        if (zuper != null) zuper.call();
                        }
                    } catch (Exception e) {
                    if (zuper != null) zuper.call();
                }
                // mark dirty and schedule/save only the corresponding config
                if (targetClass != null) {
                    container.markDirty(targetClass);
                    if (forceSyncSave || !annotation.asyncSave()) {
                        container.saveConfig(targetClass);
                    } else {
                        container.executor.submit(() -> container.saveConfig(targetClass));
                    }
                } else {
                    // fallback: save by path
                    if (forceSyncSave || !annotation.asyncSave()) {
                        container.saveConfigByPath(filePath);
                    } else {
                        container.executor.submit(() -> container.saveConfigByPath(filePath));
                    }
                }
                return null;
            }

            if (zuper != null) return zuper.call();
            return null;
        }
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
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), charset)) {
                yaml.dump(dump, writer);
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
                saveToFile(entry.delegate, entry.configClass, entry.filePath, entry.charset, entry.annotation);
            } catch (Exception e) {
                System.err.println("Error saving configuration for " + entry.delegate.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
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
            saveToFile(e.delegate, e.configClass, e.filePath, e.charset, e.annotation);
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
                saveConfig(e.delegate.getClass());
                break;
            }
        }
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
                        meta.batchId = id;
                        meta.itemId = key;
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
        String batchId;
        String itemId;
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
        try {
            Class<?> origClass = original.getClass();
            Class<?> proxyClass = new ByteBuddy()
                    .subclass(origClass)
                    .defineField(SYNTHETIC_BATCH_FIELD, String.class, net.bytebuddy.description.modifier.Visibility.PRIVATE)
                    .defineField(SYNTHETIC_FILE_FIELD, String.class, net.bytebuddy.description.modifier.Visibility.PRIVATE)
                    .make()
                    .load(origClass.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded();

            Object proxy = proxyClass.getDeclaredConstructor().newInstance();
            // copy fields
            for (Field f : origClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val = f.get(original);
                try {
                    Field targetField = null;
                    try {
                        targetField = proxyClass.getDeclaredField(f.getName());
                    } catch (NoSuchFieldException nsf) {
                        // field inherited from superclass, search hierarchy
                        Class<?> cur = proxyClass;
                        while (cur != null) {
                            try {
                                targetField = cur.getDeclaredField(f.getName());
                                break;
                            } catch (NoSuchFieldException ignored) {
                                cur = cur.getSuperclass();
                            }
                        }
                    }
                    if (targetField != null) {
                        targetField.setAccessible(true);
                        targetField.set(proxy, val);
                    }
                } catch (Throwable t) {
                    // ignore individual field copy failures
                }
            }

            // set synthetic fields
            try {
                Field b = proxyClass.getDeclaredField(SYNTHETIC_BATCH_FIELD);
                b.setAccessible(true);
                b.set(proxy, batchId);
            } catch (Throwable ex) {
                System.err.println("Unable to set synthetic batch field: " + SYNTHETIC_BATCH_FIELD + " - " + ex.getMessage());
                ex.printStackTrace();
            }
            try {
                Field f = proxyClass.getDeclaredField(SYNTHETIC_FILE_FIELD);
                f.setAccessible(true);
                f.set(proxy, filePath);
            } catch (Throwable ex) {
                System.err.println("Unable to set synthetic file field: " + SYNTHETIC_FILE_FIELD + " - " + ex.getMessage());
                ex.printStackTrace();
            }

            return proxy;
        } catch (Exception e) {
            // fallback: attach nothing, return original
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
            if (isPrimitiveOrString(val.getClass())) {
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


