package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.cache.Cache;
import net.vortexdevelopment.vinject.database.cache.CacheManager;
import net.vortexdevelopment.vinject.database.repository.EntityMetadata;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryUtils;
import net.vortexdevelopment.vinject.database.repository.SerializedFieldInfo;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
import net.vortexdevelopment.vinject.debug.DebugLogger;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles standard CRUD operations from CrudRepository.
 */
public class CrudMethodHandler extends BaseMethodHandler {

    private boolean isPreloaded = false;

    public static final Set<String> SUPPORTED_METHODS = Set.of(
            "save", "saveAll", "findById", "existsById", "findAll", "findAllById", "count",
            "deleteById", "delete", "deleteAllById", "deleteAll"
    );

    @Override
    public boolean canHandle(Method method) {
        return SUPPORTED_METHODS.contains(method.getName());
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        return switch (methodName) {
            case "save" -> save(context, args[0]);
            case "saveAll" -> saveAll(context, (Iterable<?>) args[0]);
            case "findById" -> findById(context, args[0]);
            case "existsById" -> existsById(context, args[0]);
            case "findAll" -> {
                if (args == null || args.length == 0) yield findAll(context);
                if (args.length == 1 && args[0] instanceof Iterable) yield findAllById(context, (Iterable<?>) args[0]);
                throw new IllegalArgumentException("Invalid arguments for findAll");
            }
            case "count" -> count(context);
            case "deleteById" -> {
                yield deleteById(context, args[0]);
            }
            case "delete" -> {
                yield delete(context, args[0]);
            }
            case "deleteAllById" -> {
                deleteAllById(context, (Iterable<?>) args[0]);
                yield null;
            }
            case "deleteAll" -> {
                if (args == null || args.length == 0) {
                    deleteAll(context);
                } else if (args.length == 1 && args[0] instanceof Iterable) {
                    yield deleteAll(context, (Iterable<?>) args[0]);
                }
                yield null;
            }
            default -> null;
        };
    }

    private Cache<Object, Object> getCache(RepositoryInvocationContext<?, ?> context) {
        DependencyContainer container = context.getDependencyContainer();
        // Check if CacheManager is available without throwing exception
        CacheManager cacheManager = container.getDependencyOrNull(CacheManager.class);
        if (cacheManager == null) {
            return null;
        }

        String cacheName = context.getRepositoryClass().getName();
        // Try to get existing cache
        Cache<Object, Object> cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            return cache;
        }

        // Find all @EnableCaching annotations in hierarchy (leaf to root)
        java.util.List<net.vortexdevelopment.vinject.annotation.database.EnableCaching> annotations = 
            net.vortexdevelopment.vinject.di.utils.DependencyUtils.findAllAnnotations(
                context.getRepositoryClass(), 
                net.vortexdevelopment.vinject.annotation.database.EnableCaching.class);
        
        if (annotations.isEmpty()) {
            return null;
        }

        // Reverse to apply from root to leaf (overriding parent with child)
        java.util.Collections.reverse(annotations);

        // Start with framework defaults
        net.vortexdevelopment.vinject.database.cache.CacheConfig.CacheConfigBuilder configBuilder = 
            net.vortexdevelopment.vinject.database.cache.CacheConfig.builder()
                .policy(net.vortexdevelopment.vinject.database.cache.CachePolicy.LRU)
                .maxSize(1000)
                .hotTierSize(100)
                .ttlSeconds(300)
                .writeStrategy(net.vortexdevelopment.vinject.database.cache.WriteStrategy.WRITE_THROUGH)
                .flushIntervalSeconds(10)
                .enabled(true)
                .preload(false)
                .resolverClass(net.vortexdevelopment.vinject.database.cache.CacheResolver.class);

        // Apply values from annotations (root to leaf)
        boolean hasExplicitConfig = false;

        for (net.vortexdevelopment.vinject.annotation.database.EnableCaching ann : annotations) {
            hasExplicitConfig = true;

            if (ann.policy() != net.vortexdevelopment.vinject.database.cache.CachePolicy.UNDEFINED) {
                configBuilder.policy(ann.policy());
            }
            if (ann.maxSize() != -1) {
                configBuilder.maxSize(ann.maxSize());
            }
            if (ann.hotTierSize() != -1) {
                configBuilder.hotTierSize(ann.hotTierSize());
            }
            if (ann.ttlSeconds() != -1) {
                configBuilder.ttlSeconds(ann.ttlSeconds());
            }
            if (ann.writeStrategy() != net.vortexdevelopment.vinject.database.cache.WriteStrategy.UNDEFINED) {
                configBuilder.writeStrategy(ann.writeStrategy());
            }
            if (ann.flushIntervalSeconds() != -1) {
                configBuilder.flushIntervalSeconds(ann.flushIntervalSeconds());
            }
            if (ann.resolver() != net.vortexdevelopment.vinject.database.cache.CacheResolver.class) {
                configBuilder.resolverClass(ann.resolver());
            }
            // Always take the leaf value for these
            configBuilder.enabled(ann.enabled());
            configBuilder.preload(ann.preload());
        }

        if (hasExplicitConfig) {
            net.vortexdevelopment.vinject.database.cache.CacheConfig config = configBuilder.build();
            
            if (!config.isEnabled()) {
                return null;
            }

            if (config.getPolicy() == net.vortexdevelopment.vinject.database.cache.CachePolicy.CUSTOM && 
                config.getResolverClass() != net.vortexdevelopment.vinject.database.cache.CacheResolver.class) {
                
                net.vortexdevelopment.vinject.database.cache.CacheResolver resolver = 
                    container.getDependency(config.getResolverClass());
                
                cache = resolver.resolve(context, config);
                cacheManager.registerCache(cacheName, cache);
            } else {
                cacheManager.createCache(cacheName, config);
                cache = cacheManager.getCache(cacheName);
            }

            // Handle preloading for STATIC policy
            if (config.getPolicy() == net.vortexdevelopment.vinject.database.cache.CachePolicy.STATIC && 
                config.isPreload() && !isPreloaded) {
                isPreloaded = true;
                try {
                    DebugLogger.log(context.getRepositoryClass(), "Preloading static cache for %s", cacheName);
                    findAll(context);
                } catch (Exception e) {
                    DebugLogger.log(context.getRepositoryClass(), "Failed to preload cache: %s", e.getMessage());
                }
            }
            
            return cache;
        }

        return null;
    }

    /**
     * Injects an entity into the cache.
     */
    public void injectIntoCache(RepositoryInvocationContext<?, ?> context, Object entity) {
        Cache<Object, Object> cache = getCache(context);
        if (cache == null) return;

        Object id = context.getEntityMetadata().getPrimaryKeyFieldContent(entity);
        if (id != null) {
            cache.put(id, entity);
            DebugLogger.log(context.getRepositoryClass(), "Manually injected entity into cache: %s", id);
        }
    }

    /**
     * Removes an entity from the cache.
     */
    public void removeFromCache(RepositoryInvocationContext<?, ?> context, Object entity) {
        Cache<Object, Object> cache = getCache(context);
        if (cache == null) return;

        Object id = context.getEntityMetadata().getPrimaryKeyFieldContent(entity);
        if (id != null) {
            cache.remove(id);
            DebugLogger.log(context.getRepositoryClass(), "Manually removed entity from cache: %s", id);
        }
    }

    /**
     * Loads entities by a namespace value and injects them into the cache.
     */
    public void loadByNamespace(RepositoryInvocationContext<?, ?> context, String namespace, Object value) {
        EntityMetadata metadata = context.getEntityMetadata();
        Field field = metadata.getAutoLoadFields().get(namespace);
        if (field == null) return;

        String columnName = metadata.getSerializedColumnNames(field.getName()).get(0);
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(columnName) + " = ?";

        try {
            context.getDatabase().connect(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, value);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            Object entity = mapEntity(context, connection, context.getEntityClass(), rs);
                            if (entity != null) {
                                injectIntoCache(context, entity);
                            }
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            DebugLogger.log(context.getRepositoryClass(), "Failed to auto-load entities for namespace %s: %s", namespace, e.getMessage());
        }
    }

    /**
     * Invalidates entities by a namespace value in the cache.
     * Note: This only works if we load them first or if the ID is known.
     * Since we might not know the PK, we load and then remove.
     */
    public void invalidateByNamespace(RepositoryInvocationContext<?, ?> context, String namespace, Object value) {
        EntityMetadata metadata = context.getEntityMetadata();
        Field field = metadata.getAutoLoadFields().get(namespace);
        if (field == null) return;

        // For invalidation, we first need to find which entities to remove if we don't know their PKs
        String columnName = metadata.getSerializedColumnNames(field.getName()).get(0);
        String sql = "SELECT " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + 
                " FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(columnName) + " = ?";

        try {
            context.getDatabase().connect(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, value);
                    try (ResultSet rs = statement.executeQuery()) {
                        Cache<Object, Object> cache = getCache(context);
                        if (cache == null) return null;
                        
                        while (rs.next()) {
                            Object id = rs.getObject(1);
                            if (id != null) {
                                cache.remove(id);
                                DebugLogger.log(context.getRepositoryClass(), "Proactively invalidated entity from cache by namespace %s: %s", namespace, id);
                            }
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            DebugLogger.log(context.getRepositoryClass(), "Failed to auto-invalidate entities for namespace %s: %s", namespace, e.getMessage());
        }
    }

    private Object save(RepositoryInvocationContext<?, ?> context, Object entity) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        Field pkField = metadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);

        if (pkValue == null || !existsByIdInternal(context, pkValue)) {
            insert(context, entity);
            // Re-read PK value after insert (for auto-generated keys)
            pkValue = pkField.get(entity);
        } else {
            update(context, entity);
        }
        
        // Update cache
        Cache<Object, Object> cache = getCache(context);
        if (cache != null && pkValue != null) {
            cache.put(pkValue, entity);
        }
        
        return entity;
    }

    private Iterable<?> saveAll(RepositoryInvocationContext<?, ?> context, Iterable<?> entities) throws Exception {
        return context.getDatabase().transaction(connection -> {
            List<Object> result = new ArrayList<>();
            Cache<Object, Object> cache = getCache(context);
            Field pkField = context.getEntityMetadata().getPrimaryKeyField();
            
            for (Object entity : entities) {
                save(context, entity);
                result.add(entity);
                
                // Update cache for each entity
                if (cache != null) {
                    try {
                        Object pkValue = pkField.get(entity);
                        if (pkValue != null) {
                            cache.put(pkValue, entity);
                        }
                    } catch (IllegalAccessException e) {
                        // Ignore cache update error
                    }
                }
            }
            return result;
        });
    }

    private Object findById(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        // Check cache first
        Cache<Object, Object> cache = getCache(context);
        if (cache != null) {
            long cacheStart = System.nanoTime();
            Object cached = cache.get(id);
            long cacheEnd = System.nanoTime();
            long cacheNano = cacheEnd - cacheStart;
            
            if (cached != null) {
                DebugLogger.log(context.getRepositoryClass(), "Cache HIT for ID %s. Time: %d ns (%.3f ms)",
                        id, cacheNano, cacheNano / 1_000_000.0);
                return cached;
            } else {
                DebugLogger.log(context.getRepositoryClass(), "Cache MISS for ID %s. Time: %d ns (%.3f ms)",
                        id, cacheNano, cacheNano / 1_000_000.0);
            }
        }

        EntityMetadata metadata = context.getEntityMetadata();
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " = ?";
        long dbStart = System.nanoTime();
        Object result = context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return mapEntity(context, connection, context.getEntityClass(), rs);
                    }
                }
            }
            return null;
        });
        long dbEnd = System.nanoTime();
        long dbNano = dbEnd - dbStart;
        DebugLogger.log(context.getRepositoryClass(), "DB QUERY: %s [ID: %s]. Time: %d ns (%.3f ms)",
                sql, id, dbNano, dbNano / 1_000_000.0);

        // Update cache if found
        if (result != null && cache != null) {
            cache.put(id, result);
        }
        
        return result;
    }

    private boolean existsById(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        // Check cache first - if object is in cache, it exists
        Cache<Object, Object> cache = getCache(context);
        if (cache != null) {
            long cacheStart = System.nanoTime();
            Object cached = cache.get(id);
            long cacheEnd = System.nanoTime();
            long cacheNano = cacheEnd - cacheStart;
            
            if (cached != null) {
                DebugLogger.log(context.getRepositoryClass(), "Cache HIT (exists) for ID %s. Time: %d ns (%.3f ms)",
                        id, cacheNano, cacheNano / 1_000_000.0);
                return true;
            } else {
                DebugLogger.log(context.getRepositoryClass(), "Cache MISS (exists) for ID %s. Time: %d ns (%.3f ms)",
                        id, cacheNano, cacheNano / 1_000_000.0);
            }
        }
        return existsByIdInternal(context, id);
    }

    private boolean existsByIdInternal(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        String sql = "SELECT 1 FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " = ?";
        long dbStart = System.nanoTime();
        boolean exists = context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }
            }
        });
        long dbEnd = System.nanoTime();
        long dbNano = dbEnd - dbStart;
        DebugLogger.log(context.getRepositoryClass(), "DB QUERY (exists): %s [ID: %s]. Time: %d ns (%.3f ms)",
                sql, id, dbNano, dbNano / 1_000_000.0);
        return exists;
    }

    private @NotNull Iterable<?> findAll(RepositoryInvocationContext<?, ?> context) throws Exception {
        // findAll usually bypasses ID-based cache unless we have a "all" key or query cache
        // For now, standard behavior: read from DB, populate cache
        
        EntityMetadata metadata = context.getEntityMetadata();
        long dbStart = System.nanoTime();
        Iterable<?> results = context.getDatabase().connect(connection -> {
            String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName());
            List<Object> list = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                
                Cache<Object, Object> cache = getCache(context);
                Field pkField = metadata.getPrimaryKeyField();
                
                while (rs.next()) {
                    Object entity = mapEntity(context, connection, context.getEntityClass(), rs);
                    list.add(entity);
                    
                    // Populate cache
                    if (cache != null) {
                        try {
                            Object id = pkField.get(entity);
                            if (id != null) {
                                cache.put(id, entity);
                            }
                        } catch (IllegalAccessException e) {
                            // Ignore
                        }
                    }
                }
            }
            return list;
        });
        long dbEnd = System.nanoTime();
        long dbNano = dbEnd - dbStart;
        DebugLogger.log(context.getRepositoryClass(), "DB QUERY (findAll): SELECT * FROM %s. Time: %d ns (%.3f ms)",
                metadata.getTableName(), dbNano, dbNano / 1_000_000.0);
        return results;
    }

    private Iterable<?> findAllById(RepositoryInvocationContext<?, ?> context, Iterable<?> ids) throws Exception {
        List<Object> idList = new ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }

        // Try to fetch from cache for each ID, only query DB for missing
        Cache<Object, Object> cache = getCache(context);
        List<Object> results = new ArrayList<>();
        List<Object> missingIds = new ArrayList<>();
        
        if (cache != null) {
            long cacheStart = System.nanoTime();
            for (Object id : idList) {
                Object cached = cache.get(id);
                if (cached != null) {
                    results.add(cached);
                } else {
                    missingIds.add(id);
                }
            }
            long cacheEnd = System.nanoTime();
            long cacheNano = cacheEnd - cacheStart;
            
            DebugLogger.log(context.getRepositoryClass(), "Cache lookup (findAllById) for %d IDs: %d hits, %d misses. Time: %d ns (%.3f ms)",
                    idList.size(), results.size(), missingIds.size(), cacheNano, cacheNano / 1_000_000.0);
        } else {
            missingIds.addAll(idList);
        }
        
        if (missingIds.isEmpty()) {
            return results;
        }

        EntityMetadata metadata = context.getEntityMetadata();
        String placeholders = missingIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " IN (" + placeholders + ")";

        long dbStart = System.nanoTime();
        List<Object> dbResults = context.getDatabase().connect(connection -> {
            List<Object> list = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (Object id : missingIds) {
                    statement.setObject(index++, id);
                }
                try (ResultSet rs = statement.executeQuery()) {
                    Field pkField = metadata.getPrimaryKeyField();
                    while (rs.next()) {
                        Object entity = mapEntity(context, connection, context.getEntityClass(), rs);
                        list.add(entity);
                        
                        // Populate cache
                        if (cache != null) {
                            try {
                                Object id = pkField.get(entity);
                                if (id != null) {
                                    cache.put(id, entity);
                                }
                            } catch (IllegalAccessException e) {
                                // Ignore
                            }
                        }
                    }
                }
            }
            return list;
        });
        long dbEnd = System.nanoTime();
        long dbNano = dbEnd - dbStart;
        DebugLogger.log(context.getRepositoryClass(), "DB QUERY (findAllById): %s. Time: %d ns (%.3f ms)",
                sql, dbNano, dbNano / 1_000_000.0);
        
        results.addAll(dbResults);
        return results;
    }

    private long count(RepositoryInvocationContext<?, ?> context) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        String sql = "SELECT COUNT(*) FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName());
        return context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0L;
        });
    }

    private int deleteById(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        
        // Remove from cache
        Cache<Object, Object> cache = getCache(context);
        if (cache != null) {
            cache.remove(id);
        }
        
        String sql = "DELETE FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " = ?";
        return context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                return statement.executeUpdate();
            }
        });
    }

    private int delete(RepositoryInvocationContext<?, ?> context, Object entity) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        Field pkField = metadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);
        if (pkValue == null) {
            throw new IllegalArgumentException("Entity primary key cannot be null for deletion.");
        }
        return deleteById(context, pkValue);
    }

    private int deleteAllById(RepositoryInvocationContext<?, ?> context, Iterable<?> ids) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        List<Object> idList = new ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return 0;
        }

        // Remove from cache
        Cache<Object, Object> cache = getCache(context);
        if (cache != null) {
            for (Object id : idList) {
                cache.remove(id);
            }
        }

        String placeholders = idList.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "DELETE FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " IN (" + placeholders + ")";

        return context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (Object id : idList) {
                    statement.setObject(index++, id);
                }
                return statement.executeUpdate();
            }
        });
    }

    private int deleteAll(RepositoryInvocationContext<?, ?> context, Iterable<?> entities) throws Exception {
        List<Object> ids = new ArrayList<>();
        Field pkField = context.getEntityMetadata().getPrimaryKeyField();
        for (Object entity : entities) {
            Object id = pkField.get(entity);
            if (id != null) {
                ids.add(id);
            }
        }
        return deleteAllById(context, ids);
    }

    private void deleteAll(RepositoryInvocationContext<?, ?> context) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        
        // Invalidate entire cache
        Cache<Object, Object> cache = getCache(context);
        if (cache != null) {
            cache.invalidate();
        }
        
        String sql = "DELETE FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName());
        context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        });
    }

    private void insert(RepositoryInvocationContext<?, ?> context, Object entity) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        Set<String> processedSerializedFields = new HashSet<>();

        for (Map.Entry<String, String> entry : metadata.getFieldToColumnMap().entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            if (metadata.isSerializedColumn(columnName)) {
                SerializedFieldInfo serializedInfo = metadata.getSerializedFieldInfo(columnName);
                String originalFieldName = serializedInfo.originalField().getName();

                if (processedSerializedFields.contains(originalFieldName)) {
                    continue;
                }
                processedSerializedFields.add(originalFieldName);

                Field originalField = serializedInfo.originalField();
                Object objectValue = originalField.get(entity);

                DatabaseSerializer<Object> serializer = serializedInfo.getSerializer();
                Map<String, Object> serializedValues = serializer.serialize(objectValue);

                List<String> serializedColumnNames = metadata.getSerializedColumnNames(originalFieldName);
                for (String serializedColumnName : serializedColumnNames) {
                    String key;
                    if (serializedInfo.usePrefix()) {
                        String prefix = serializedInfo.baseColumnName() + "_";
                        if (serializedColumnName.startsWith(prefix)) {
                            key = serializedColumnName.substring(prefix.length());
                        } else {
                            key = serializedColumnName;
                        }
                    } else {
                        key = serializedColumnName;
                    }
                    Object serializedValue = serializedValues.get(key);

                    columns.add(context.getSchemaFormatter().formatColumnName(serializedColumnName));
                    values.add(serializedValue);
                    placeholders.add("?");
                }
                continue;
            }

            Field field = metadata.getField(fieldName);
            if (field == null || field.getName().equals("modifiedFields")) continue;

            // Skip if field doesn't have @Column, @Temporal, or is not the primary key
            boolean isPrimaryKey = field.equals(metadata.getPrimaryKeyField());
            boolean hasColumnAnnotation = field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Temporal.class);
            
            if (!isPrimaryKey && !hasColumnAnnotation) {
                continue;
            }

            // Skip auto-generated primary keys
            if (isPrimaryKey && RepositoryUtils.isAutoGenerated(field)) {
                continue;
            }

            Object value = field.get(entity);

            if (field.isAnnotationPresent(Temporal.class)) {
                value = (value == null) ? null : new Timestamp((long) value);
            }

            columns.add(context.getSchemaFormatter().formatColumnName(columnName));
            values.add(value);
            placeholders.add("?");
        }

        String sql = "INSERT INTO " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) + " (" +
                String.join(", ", columns) + ") VALUES (" +
                String.join(", ", placeholders) + ")";

        context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                RepositoryUtils.setStatementParameters(statement, values);
                statement.executeUpdate();

                if (RepositoryUtils.isAutoGenerated(metadata.getPrimaryKeyField())) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            Object generatedId = generatedKeys.getObject(1);
                            generatedId = RepositoryUtils.convertValueToFieldType(generatedId, metadata.getPrimaryKeyField().getType());
                            metadata.getPrimaryKeyField().set(entity, generatedId);
                        }
                    }
                }
            }
        });
    }

    private void update(RepositoryInvocationContext<?, ?> context, Object entity) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        Set<String> processedSerializedFields = new HashSet<>();

        for (Map.Entry<String, String> entry : metadata.getFieldToColumnMap().entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            if (metadata.isSerializedColumn(columnName)) {
                SerializedFieldInfo serializedInfo = metadata.getSerializedFieldInfo(columnName);
                String originalFieldName = serializedInfo.originalField().getName();

                if (processedSerializedFields.contains(originalFieldName)) {
                    continue;
                }

                try {
                    Method isFieldModified = entity.getClass().getDeclaredMethod("isFieldModified", String.class);
                    if (!(boolean) isFieldModified.invoke(entity, originalFieldName)) {
                        continue;
                    }
                } catch (NoSuchMethodException ignored) {}

                processedSerializedFields.add(originalFieldName);

                Field originalField = serializedInfo.originalField();
                Object objectValue = originalField.get(entity);

                DatabaseSerializer<Object> serializer = serializedInfo.getSerializer();
                Map<String, Object> serializedValues = serializer.serialize(objectValue);

                List<String> serializedColumnNames = metadata.getSerializedColumnNames(originalFieldName);
                for (String serializedColumnName : serializedColumnNames) {
                    String key;
                    if (serializedInfo.usePrefix()) {
                        String prefix = serializedInfo.baseColumnName() + "_";
                        if (serializedColumnName.startsWith(prefix)) {
                            key = serializedColumnName.substring(prefix.length());
                        } else {
                            key = serializedColumnName;
                        }
                    } else {
                        key = serializedColumnName;
                    }
                    Object serializedValue = serializedValues.get(key);

                    setClauses.add(context.getSchemaFormatter().formatColumnName(serializedColumnName) + " = ?");
                    values.add(serializedValue);
                }
                continue;
            }

            try {
                Method isFieldModified = entity.getClass().getDeclaredMethod("isFieldModified", String.class);
                if (!(boolean) isFieldModified.invoke(entity, fieldName)) {
                    continue;
                }
            } catch (NoSuchMethodException ignored) {}

            if (fieldName.equals(metadata.getPrimaryKeyField().getName())) {
                continue;
            }

            Field field = metadata.getField(fieldName);
            if (field == null) continue;

            if (!field.isAnnotationPresent(Column.class) && !field.isAnnotationPresent(Temporal.class)) {
                continue;
            }

            Object value = field.get(entity);

            Temporal temporal = field.getAnnotation(Temporal.class);
            if (temporal != null) {
                value = (value == null) ? null : new Timestamp((long) value);
            }

            setClauses.add(context.getSchemaFormatter().formatColumnName(columnName) + " = ?");
            values.add(value);
        }

        if (values.isEmpty()) {
            return;
        }

        try {
            Method resetModifiedFields = entity.getClass().getDeclaredMethod("resetModifiedFields");
            resetModifiedFields.setAccessible(true);
            resetModifiedFields.invoke(entity);
        } catch (Exception ignored) {}

        String sql = "UPDATE " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) + " SET " +
                String.join(", ", setClauses) + " WHERE " +
                context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " = ?";

        Field pkField = metadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);
        values.add(pkValue);

        context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                RepositoryUtils.setStatementParameters(statement, values);
                statement.executeUpdate();
            } catch (Exception e) {
                System.err.println("Error executing update statement: " + sql);
                e.printStackTrace();
            }
        });
    }
}
