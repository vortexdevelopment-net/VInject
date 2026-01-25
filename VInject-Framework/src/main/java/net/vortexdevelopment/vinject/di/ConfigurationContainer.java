package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.util.Injectable;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlSerializer;
import net.vortexdevelopment.vinject.config.ConfigurationSection;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.yaml.RenderOptions;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import net.vortexdevelopment.vinject.di.config.ConfigurationMapper;
import net.vortexdevelopment.vinject.di.scan.ClasspathScanner;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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
    private final ConfigurationMapper mapper;
    private final Map<Class<?>, ConfigEntry> configs = new ConcurrentHashMap<>();
    // private final Map<Class<?>, YamlSerializerBase<?>> serializers = new ConcurrentHashMap<>(); // Moved to ConfigurationMapper
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // Track which config classes write to which file, and which top-level keys they own
    private final Map<String, Map<String, Class<?>>> fileToKeyToClass = new ConcurrentHashMap<>();
    private static final String SYNTHETIC_BATCH_FIELD = "__vinject_yaml_batch_id";
    private static final String SYNTHETIC_FILE_FIELD = "__vinject_yaml_file";
    
    // ConfigurationSection support
    // Per-file YamlConfig instances: filePath -> YamlConfig
    private final Map<String, YamlConfig> fileDataMaps = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> batchStores = new ConcurrentHashMap<>();
    private final Map<String, Object> fileWriteLocks = new ConcurrentHashMap<>();

    private static final class BatchInfo {
        final String rootKey;
        final String baseDir;
        final Class<?> targetClass;
        final Object holderInstance;

        BatchInfo(String rootKey, String baseDir, Class<?> targetClass, Object holderInstance) {
            this.rootKey = rootKey;
            this.baseDir = baseDir;
            this.targetClass = targetClass;
            this.holderInstance = holderInstance;
        }
    }
    private final Map<String, BatchInfo> batches = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> directoryClassToBatchId = new ConcurrentHashMap<>();

    public ConfigurationContainer(DependencyContainer container, ClasspathScanner scanner, Reflections reflections, Set<Class<?>> yamlConfigClasses) {
        INSTANCE = this;
        this.container = container;
        this.mapper = new ConfigurationMapper(container);
        
        // 1. Auto-register YamlSerializerBase implementations first
        scanner.scanAndFilter(YamlSerializer.class, container::canLoadClass).forEach(serializerClass -> {
            try {
                Object instance = container.newInstance(serializerClass);
                if (instance instanceof YamlSerializerBase<?> ys) {
                    registerSerializer(ys);
                } else {
                    throw new RuntimeException("Class " + serializerClass.getName() + " is annotated with @YamlSerializer but does not implement YamlSerializerBase.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to register YamlSerializerBase: " + serializerClass.getName(), e);
            }
        });

        // 2. Load configurations
        loadConfigs(container, yamlConfigClasses);

        // 3. Load batch directories
        try {
            loadBatches(reflections, container);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load YAML batch directories", e);
        }

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
                Object instance = container.newInstance(cfgClass);
                File file = resolvedPath.toFile();
                
                // Use YamlConfig to load the file
                YamlConfig config = fileDataMaps.get(filePath);
                if (config == null) {
                    config = YamlConfig.load(file);
                    fileDataMaps.put(filePath, config);
                }

                // Map data to instance
                mapper.mapToInstance(config, instance, cfgClass, annotation.path());

                // Invoke @OnLoad methods after all data is loaded
                if (container != null) {
                    container.getLifecycleManager().invokeOnLoad(instance);
                }

                // Initial save if merging added keys (handled by saveToFile)
                try {
                    saveToFile(instance, cfgClass, filePath, charset, annotation);
                } catch (Exception ignored) {
                }

                // Store proxy as the delegate for saving/inspection (store original class too)
                configs.put(cfgClass, new ConfigEntry(cfgClass, filePath, charset, annotation));
                
                // Track which keys belong to which class for sorting when multiple classes write to same file
                Set<String> keys = config.getKeys(false);
                Map<String, Class<?>> keyToClass = fileToKeyToClass.computeIfAbsent(filePath, k -> new ConcurrentHashMap<>());
                for (String topLevelKey : keys) {
                    keyToClass.put(topLevelKey, cfgClass);
                }
            } catch (Exception e) {
                System.err.println("Error loading configuration class " + cfgClass.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
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

    private void saveToFile(Object instance, Class<?> cfgClass, String filePath, Charset charset, YamlConfiguration annotation) throws Exception {
        YamlConfig config = fileDataMaps.get(filePath);
        if (config == null) {
            Path resolvedPath = resolvePath(filePath);
            config = YamlConfig.load(resolvedPath.toFile());
            fileDataMaps.put(filePath, config);
        }

        // Apply instance data to config
        mapper.applyToConfig(config, instance, cfgClass, annotation.path());

        // Concurrent safety for file writes
        Object lock = fileWriteLocks.computeIfAbsent(filePath, k -> new Object());
        synchronized (lock) {
            config.save();
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
                    System.err.println("ERROR: saveAllSync - Could not get instance for class: " + entry.configClass.getName());
                    continue;
                }
                saveToFile(instance, entry.configClass, entry.filePath, entry.charset, entry.annotation);
            } catch (Exception e) {
                System.err.println("Error saving configuration for " + entry.configClass.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    public void registerSerializer(YamlSerializerBase<?> serializer) {
        mapper.registerSerializer(serializer);
    }

    public Object getConfigValue(Class<?> configClass, String path) {
        ConfigEntry entry = configs.get(configClass);
        if (entry != null) {
            YamlConfig config = fileDataMaps.get(entry.filePath);
            if (config != null) {
                String fullPath = entry.annotation.path();
                if (fullPath != null && !fullPath.isEmpty()) {
                    fullPath = fullPath + "." + path;
                } else {
                    fullPath = path;
                }
                return config.get(fullPath);
            }
        }
        return null;
    }

    public void saveConfig(Object instance) {
        if (instance == null) {
            return;
        }
        Class<?> clazz = instance.getClass();
        ConfigEntry entry = configs.get(clazz);
        if (entry != null) {
            if (forceSyncSave) {
                try {
                    saveToFile(instance, clazz, entry.filePath, entry.charset, entry.annotation);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                executor.submit(() -> {
                    try {
                        saveToFile(instance, clazz, entry.filePath, entry.charset, entry.annotation);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } else {
            // Check if it's a batch directory holder
            String batchId = directoryClassToBatchId.get(clazz);
            if (batchId != null) {
                syncBatchDirectory(clazz, batchId);
            } else {
                // Check if it's a batch item
                saveItemObject(instance);
            }
        }
    }

    public void saveConfig(Class<?> configClass, boolean force) {
        ConfigEntry e = configs.get(configClass);
        if (e == null) {
            String batchId = directoryClassToBatchId.get(configClass);
            if (batchId != null) {
                syncBatchDirectory(configClass, batchId);
            }
            return;
        }

        synchronized (e) {
            if (!force && !e.dirty) {
                return;
            }

            Object instance = container.getDependencyOrNull(e.configClass);
            if (instance == null) {
                return;
            }

            try {
                saveToFile(instance, e.configClass, e.filePath, e.charset, e.annotation);
                e.dirty = false;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void saveConfig(Class<?> configClass) {
        saveConfig(configClass, true);
    }

    public void markDirty(Class<?> configClass) {
        ConfigEntry e = configs.get(configClass);
        if (e != null) {
            e.dirty = true;
        }
    }

    public void saveItemObject(Object instance) {
        saveItemObject(instance, true);
    }

    public void saveItemObject(Object instance, boolean includeComments) {
        if (instance == null) {
            return;
        }
        String batchId = readSyntheticStringField(instance, SYNTHETIC_BATCH_FIELD);
        String filePath = readSyntheticStringField(instance, SYNTHETIC_FILE_FIELD);
        
        if (batchId == null || filePath == null) {
            return;
        }

        YamlConfig config = fileDataMaps.get(filePath);
        if (config == null) {
            config = YamlConfig.load(resolvePath(filePath).toFile());
            fileDataMaps.put(filePath, config);
        }

        try {
            Field idField = mapper.findIdFieldForClass(instance.getClass());
            if (idField == null) {
                return;
            }
            idField.setAccessible(true);
            String itemId = (String) idField.get(instance);
            
            BatchInfo info = batches.get(batchId);
            String rootKey = (info != null) ? info.rootKey : "";
            String fullPath = (rootKey == null || rootKey.isEmpty()) ? itemId : rootKey + "." + itemId;

            mapper.applyToConfig(config, instance, instance.getClass(), fullPath);
            RenderOptions options = new RenderOptions().setIncludeComments(includeComments);
            if (forceSyncSave) {
                config.save(options);
            } else {
                YamlConfig finalConfig = config;
                executor.submit(() -> {
                    try {
                        finalConfig.save(options);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveBatch(String batchId) {
        saveBatch(batchId, true);
    }

    public void saveBatch(String batchId, boolean includeComments) {
        Map<String, Object> items = batchStores.get(batchId);
        if (items != null) {
            for (Object item : items.values()) {
                saveItemObject(item, includeComments);
            }
        }
    }

    public void saveItem(String batchId, String itemId) {
        saveItem(batchId, itemId, true);
    }

    public void saveItem(String batchId, String itemId, boolean includeComments) {
        Map<String, Object> items = batchStores.get(batchId);
        if (items != null) {
            Object item = items.get(itemId);
            if (item != null) {
                saveItemObject(item, includeComments);
            }
        }
    }

    public void registerItemObject(Class<?> holderClass, String fileName, Object item) {
        String batchId = directoryClassToBatchId.get(holderClass);
        if (batchId == null) {
            throw new IllegalArgumentException("Holder class " + holderClass.getName() + " is not a registered YamlDirectory.");
        }

        BatchInfo info = batches.get(batchId);
        if (!info.targetClass.isAssignableFrom(item.getClass())) {
            throw new IllegalArgumentException("Item of type " + item.getClass().getName() + " cannot be added to batch " + batchId + " (expected " + info.targetClass.getName() + ")");
        }

        String actualFileName = fileName;
        if (!actualFileName.endsWith(".yml") && !actualFileName.endsWith(".yaml")) {
            actualFileName += ".yml";
        }

        // Resolve absolute path for synthetic field
        Path filePath = resolvePath(info.baseDir).resolve(actualFileName).toAbsolutePath();
        
        // Instrument
        instrumentItemInstance(item, batchId, filePath.toString());

        // Registry update
        Map<String, Object> items = batchStores.computeIfAbsent(batchId, k -> new LinkedHashMap<>());
        
        // Try to find the ID from @YamlId
        String itemId = null;
        try {
            Field idField = mapper.findIdFieldForClass(item.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                itemId = (String) idField.get(item);
            }
        } catch (Exception ignored) {}
        
        if (itemId == null || itemId.isEmpty()) {
            // Fallback to filename without extension
            int dotIdx = actualFileName.lastIndexOf('.');
            itemId = dotIdx == -1 ? actualFileName : actualFileName.substring(0, dotIdx);
        }

        items.put(itemId, item);
        
        // Update holder collections
        if (info.holderInstance != null) {
            updateHolderCollections(info.holderInstance, holderClass, items);
        }
    }

    public void registerAndSaveItemObject(Class<?> holderClass, String fileName, Object item) {
        registerItemObject(holderClass, fileName, item);
        saveItemObject(item);
    }

    private void updateHolderCollections(Object holderInstance, Class<?> holderClass, Map<String, Object> loadedItems) {
        for (Field field : holderClass.getDeclaredFields()) {
            boolean isMap = Map.class.isAssignableFrom(field.getType());
            boolean isCollection = Collection.class.isAssignableFrom(field.getType());
            boolean hasAnnotation = field.isAnnotationPresent(YamlCollection.class);

            if (isMap || isCollection || hasAnnotation) {
                field.setAccessible(true);
                try {
                    Object colValue = field.get(holderInstance);
                    if (colValue == null) continue;

                    if (isMap) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> holderMap = (Map<Object, Object>) colValue;
                        holderMap.putAll(loadedItems);
                    } else if (isCollection) {
                        @SuppressWarnings("unchecked")
                        Collection<Object> holderColl = (Collection<Object>) colValue;
                        for (Object val : loadedItems.values()) {
                            if (!holderColl.contains(val)) {
                                holderColl.add(val);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public void loadBatches(org.reflections.Reflections reflections, DependencyContainer container) {
        for (Class<?> annotated : reflections.getTypesAnnotatedWith(YamlDirectory.class)) {
            YamlDirectory ann = annotated.getAnnotation(YamlDirectory.class);
            if (ann == null) {
                continue;
            }
            loadBatchDirectory(ann, annotated);
        }
    }

    public String loadBatchDirectory(YamlDirectory ann, Class<?> holderClass) {
        String dir = ann.dir();
        String id = holderClass.getName() + "::" + dir;
        
        // Instantiate the holder class so it can be injected and used to access the batch
        Object holderInstance = container.newInstance(holderClass);
        
        batches.put(id, new BatchInfo(ann.rootKey(), dir, ann.target(), holderInstance));
        directoryClassToBatchId.put(holderClass, id);
        
        Path start = resolvePath(dir);
        if (ann.copyDefaults()) {
            copyResourceDirectory(holderClass, dir, start);
        }

        if (!Files.exists(start)) {
            return id;
        }

        try {
            Stream<Path> pathStream = ann.recursive() ? Files.walk(start) : Files.list(start);
            pathStream.filter(p -> Files.isRegularFile(p) && (p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")))
                  .forEach(p -> {
                      try {
                          YamlConfig config = YamlConfig.load(p.toFile());
                          ConfigurationSection source = (ann.rootKey() == null || ann.rootKey().isEmpty()) 
                                  ? config : config.getConfigurationSection(ann.rootKey());
                          
                          if (source == null) {
                              return;
                          }

                          for (String key : source.getKeys(false)) {
                              Object itemInstance = container.newInstance(ann.target(), false);
                              mapper.mapToInstance(source.getConfigurationSection(key), itemInstance, ann.target(), "");
                              
                              Field idField = mapper.findIdFieldForClass(ann.target());
                              if (idField != null) {
                                  idField.setAccessible(true);
                                  idField.set(itemInstance, key);
                              }

                              instrumentItemInstance(itemInstance, id, p.toAbsolutePath().toString());
                              
                              Map<String, Object> items = batchStores.computeIfAbsent(id, k -> new LinkedHashMap<>());
                              items.put(key, itemInstance);
                              
                              if (container != null) {
                                  container.getLifecycleManager().invokeOnLoad(itemInstance);
                              }
                          }
                      } catch (Exception e) {
                          e.printStackTrace();
                      }
                  });
            pathStream.close();

            // Populate any Map or Collection fields in the holder instance
            if (holderInstance != null) {
                Map<String, Object> loadedItems = batchStores.get(id);
                if (loadedItems != null) {
                    updateHolderCollections(holderInstance, holderClass, loadedItems);
                    if (container != null) {
                        container.getLifecycleManager().invokeOnLoad(holderInstance);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return id;
    }

    public Map<String, Object> getBatch(String id) {
        Map<String, Object> m = batchStores.get(id);
        return m == null ? Collections.emptyMap() : Collections.unmodifiableMap(m);
    }

    public ConfigurationSection getSectionForInstance(Object instance) {
        if (instance == null) {
            return null;
        }
        Class<?> clazz = instance.getClass();
        ConfigEntry entry = configs.get(clazz);
        if (entry != null) {
            YamlConfig config = fileDataMaps.get(entry.filePath);
            if (config != null) {
                String path = entry.annotation.path();
                if (path == null || path.isEmpty()) {
                    return config;
                }
                return config.getConfigurationSection(path);
            }
        }
        return null;
    }

    public void reloadConfig(Class<?> configClass) {
        ConfigEntry entry = configs.get(configClass);
        if (entry == null) {
            return;
        }
        
        YamlConfig config = YamlConfig.load(resolvePath(entry.filePath).toFile());
        fileDataMaps.put(entry.filePath, config);
        
        Object instance = container.getDependencyOrNull(configClass);
        if (instance != null) {
            try {
                mapper.mapToInstance(config, instance, configClass, entry.annotation.path());
                container.getLifecycleManager().invokeOnLoad(instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String readSyntheticStringField(Object instance, String fieldName) {
        try {
            Field f = findFieldInHierarchy(instance.getClass(), fieldName);
            if (f != null) {
                f.setAccessible(true);
                Object v = f.get(instance);
                return v != null ? v.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void instrumentItemInstance(Object instance, String batchId, String filePath) {
        try {
            Field batchField = findFieldInHierarchy(instance.getClass(), SYNTHETIC_BATCH_FIELD);
            Field fileField = findFieldInHierarchy(instance.getClass(), SYNTHETIC_FILE_FIELD);
            
            if (batchField != null && fileField != null) {
                batchField.setAccessible(true);
                fileField.setAccessible(true);
                batchField.set(instance, batchId);
                fileField.set(instance, filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void copyResourceDirectory(Class<?> holderClass, String resourceDir, Path targetPath) {
        try {
            // Ensure target directory exists
            Files.createDirectories(targetPath);

            ClassLoader loader = holderClass.getClassLoader();
            String relPath = resourceDir.startsWith("/") ? resourceDir.substring(1) : resourceDir;
            if (!relPath.endsWith("/")) relPath += "/";

            URL url = loader.getResource(relPath);
            
            // 1. Try filesystem (IDE/Local) case
            if (url != null && "file".equals(url.getProtocol())) {
                Path source = Paths.get(url.toURI());
                Files.walk(source).forEach(p -> {
                    try {
                        Path destination = targetPath.resolve(source.relativize(p));
                        if (Files.isDirectory(p)) {
                            Files.createDirectories(destination);
                        } else if (!Files.exists(destination)) {
                            Files.createDirectories(destination.getParent());
                            Files.copy(p, destination);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                return;
            }

            // 2. Fallback for JAR (iterate JAR entries)
            // Either getResource was null (missing dir entry) or it's a jar protocol
            URL codeSourceUrl = holderClass.getProtectionDomain().getCodeSource().getLocation();
            if (codeSourceUrl == null) return;

            File jarFile;
            try {
                jarFile = new File(codeSourceUrl.toURI());
            } catch (Exception e) {
                // Fallback for complex URLs
                String path = codeSourceUrl.getPath();
                if (path.startsWith("file:")) path = path.substring(5);
                if (path.contains("!")) path = path.substring(0, path.indexOf("!"));
                jarFile = new File(URLDecoder.decode(path, StandardCharsets.UTF_8.name()));
            }

            if (jarFile.exists() && jarFile.isFile()) {
                try (JarFile jar = new JarFile(jarFile)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        
                        if (name.startsWith(relPath)) {
                            String entryRelPath = name.substring(relPath.length());
                            if (entryRelPath.isEmpty()) continue;

                            Path dest = targetPath.resolve(entryRelPath);
                            if (entry.isDirectory()) {
                                Files.createDirectories(dest);
                            } else if (!Files.exists(dest)) {
                                Files.createDirectories(dest.getParent());
                                try (InputStream is = jar.getInputStream(entry)) {
                                    Files.copy(is, dest);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void syncBatchDirectory(Class<?> holderClass, String batchId) {
        if (forceSyncSave) {
            syncBatchDirectoryInternal(holderClass, batchId);
        } else {
            executor.submit(() -> syncBatchDirectoryInternal(holderClass, batchId));
        }
    }

    private void syncBatchDirectoryInternal(Class<?> holderClass, String batchId) {
        BatchInfo info = batches.get(batchId);
        if (info == null || info.holderInstance == null) return;

        Set<String> currentIds = getCurrentItemIds(info.holderInstance, holderClass);
        Map<String, Object> storedItems = batchStores.get(batchId);
        if (storedItems == null) return;

        // Copy stored IDs to safely iterate and potentially remove
        Set<String> storedIds = new HashSet<>(storedItems.keySet());

        for (String id : storedIds) {
            if (!currentIds.contains(id)) {
                // Item was removed from holder collections
                Object removedItem = storedItems.remove(id);
                if (removedItem != null) {
                    deleteItemFileEntry(removedItem, id, info);
                }
            } else {
                // Item still exists, save it
                Object item = storedItems.get(id);
                saveItemObject(item);
            }
        }
    }

    private Set<String> getCurrentItemIds(Object holderInstance, Class<?> holderClass) {
        Set<String> ids = new HashSet<>();
        for (Field field : holderClass.getDeclaredFields()) {
            boolean isMap = Map.class.isAssignableFrom(field.getType());
            boolean isCollection = Collection.class.isAssignableFrom(field.getType());
            boolean hasAnnotation = field.isAnnotationPresent(YamlCollection.class);

            if (isMap || isCollection || hasAnnotation) {
                field.setAccessible(true);
                try {
                    Object val = field.get(holderInstance);
                    if (val == null) continue;

                    if (val instanceof Map<?, ?> map) {
                        for (Object k : map.keySet()) {
                            if (k != null) ids.add(k.toString());
                        }
                    } else if (val instanceof Collection<?> col) {
                        for (Object o : col) {
                            if (o == null) continue;
                            String id = getItemId(o);
                            if (id != null) ids.add(id);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return ids;
    }

    private String getItemId(Object item) {
        try {
            Field idField = mapper.findIdFieldForClass(item.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                Object id = idField.get(item);
                return id != null ? id.toString() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void deleteItemFileEntry(Object item, String itemId, BatchInfo info) {
        String filePath = readSyntheticStringField(item, SYNTHETIC_FILE_FIELD);
        if (filePath == null) return;

        YamlConfig config = fileDataMaps.get(filePath);
        if (config == null) {
            Path path = resolvePath(filePath);
            if (!Files.exists(path)) return;
            config = YamlConfig.load(path.toFile());
            fileDataMaps.put(filePath, config);
        }

        String fullPath = (info.rootKey == null || info.rootKey.isEmpty()) ? itemId : info.rootKey + "." + itemId;
        config.set(fullPath, null); // This removes the node in YamlConfig

        try {
            boolean isEmpty;
            if (info.rootKey == null || info.rootKey.isEmpty()) {
                isEmpty = config.getKeys(false).isEmpty();
            } else {
                ConfigurationSection section = config.getConfigurationSection(info.rootKey);
                isEmpty = (section == null || section.getKeys(false).isEmpty());
                if (isEmpty && section != null) {
                    config.set(info.rootKey, null);
                    isEmpty = config.getKeys(false).isEmpty();
                }
            }

            if (isEmpty) {
                fileDataMaps.remove(filePath);
                Files.deleteIfExists(resolvePath(filePath));
            } else {
                config.save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

