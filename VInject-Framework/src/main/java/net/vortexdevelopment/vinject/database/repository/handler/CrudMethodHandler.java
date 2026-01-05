package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.repository.EntityMetadata;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryUtils;
import net.vortexdevelopment.vinject.database.repository.SerializedFieldInfo;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
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

    private Object save(RepositoryInvocationContext<?, ?> context, Object entity) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        Field pkField = metadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);

        if (pkValue == null || !existsByIdInternal(context, pkValue)) {
            insert(context, entity);
        } else {
            update(context, entity);
        }
        return entity;
    }

    private Iterable<?> saveAll(RepositoryInvocationContext<?, ?> context, Iterable<?> entities) throws Exception {
        return context.getDatabase().transaction(connection -> {
            List<Object> result = new ArrayList<>();
            for (Object entity : entities) {
                save(context, entity);
                result.add(entity);
            }
            return result;
        });
    }

    private Object findById(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " = ?";
        return context.getDatabase().connect(connection -> {
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
    }

    private boolean existsById(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        return existsByIdInternal(context, id);
    }

    private boolean existsByIdInternal(RepositoryInvocationContext<?, ?> context, Object id) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        String sql = "SELECT 1 FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " = ?";
        return context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private @NotNull Iterable<?> findAll(RepositoryInvocationContext<?, ?> context) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        return context.getDatabase().connect(connection -> {
            String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName());
            List<Object> results = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(mapEntity(context, connection, context.getEntityClass(), rs));
                }
            }
            return results;
        });
    }

    private Iterable<?> findAllById(RepositoryInvocationContext<?, ?> context, Iterable<?> ids) throws Exception {
        EntityMetadata metadata = context.getEntityMetadata();
        List<Object> idList = new ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = idList.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + context.getSchemaFormatter().formatColumnName(metadata.getPrimaryKeyColumn()) + " IN (" + placeholders + ")";

        return context.getDatabase().connect(connection -> {
            List<Object> results = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (Object id : idList) {
                    statement.setObject(index++, id);
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapEntity(context, connection, context.getEntityClass(), rs));
                    }
                }
            }
            return results;
        });
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

            if (!field.isAnnotationPresent(Column.class) && !field.isAnnotationPresent(Temporal.class)) {
                continue;
            }

            Object value = field.get(entity);

            if (field.equals(metadata.getPrimaryKeyField()) && RepositoryUtils.isAutoGenerated(field)) {
                continue;
            }

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
