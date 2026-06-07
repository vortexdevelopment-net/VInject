package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.repository.EntityMetadata;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryMethodHandler;
import net.vortexdevelopment.vinject.database.repository.RepositoryUtils;
import net.vortexdevelopment.vinject.database.repository.SerializedFieldInfo;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;

import java.lang.reflect.Field;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for repository method handlers, providing common mapping and utility logic.
 */
public abstract class BaseMethodHandler implements RepositoryMethodHandler {

    /**
     * Maps a ResultSet to an entity instance.
     *
     * @param context    The repository invocation context.
     * @param connection The database connection.
     * @param entityCls  The class of the entity to map.
     * @param resultSet  The ResultSet from the query.
     * @return The mapped entity instance.
     * @throws Exception If an error occurs during mapping.
     */
    @SuppressWarnings("unchecked")
    protected Object mapEntity(RepositoryInvocationContext<?, ?> context, Connection connection, Class<?> entityCls, ResultSet resultSet) throws Exception {
        EntityMetadata metadata = new EntityMetadata(entityCls, context.getDatabase().getSerializerRegistry());
        Object entityInstance = context.getDependencyContainer().newInstance(entityCls);

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

                List<String> serializedColumnNames = metadata.getSerializedColumnNames(originalFieldName);
                Map<String, Object> columnValues = new HashMap<>();

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
                    Object value = resultSet.getObject(serializedColumnName);
                    columnValues.put(key, value);
                }

                DatabaseSerializer<Object> serializer = serializedInfo.getSerializer();
                Object deserializedValue = serializer.deserialize(columnValues);

                Field originalField = serializedInfo.originalField();
                originalField.set(entityInstance, deserializedValue);
                continue;
            }

            Field field = metadata.getField(fieldName);
            if (field == null || field.getName().equals("modifiedFields")) {
                continue;
            }

            if (field.getType().isAnnotationPresent(Entity.class)) {
                Object foreignKeyValue = resultSet.getObject(columnName);
                if (foreignKeyValue != null) {
                    EntityMetadata foreignMetadata = new EntityMetadata(field.getType(), context.getDatabase().getSerializerRegistry());
                    Field foreignPkField = foreignMetadata.getPrimaryKeyField();
                    foreignKeyValue = RepositoryUtils.convertValueToFieldType(foreignKeyValue, foreignPkField.getType());
                    Object foreignEntity = fetchForeignEntity(context, connection, field.getType(), metadata.getPrimaryKeyColumn(), foreignKeyValue);
                    field.set(entityInstance, foreignEntity);
                }
            } else {
                Object value = resultSet.getObject(columnName);
                value = RepositoryUtils.convertValueToFieldType(value, field.getType());

                if (field.getType() == UUID.class && value instanceof String) {
                    value = UUID.fromString((String) value);
                }

                Temporal temporal = field.getAnnotation(Temporal.class);
                if (temporal != null) {
                    value = switch (temporal.value()) {
                        case DATE -> resultSet.getDate(columnName);
                        case TIME -> resultSet.getTime(columnName);
                        case TIMESTAMP -> {
                            Timestamp ts = resultSet.getTimestamp(columnName);
                            yield ts != null ? ts.getTime() : null;
                        }
                        default -> throw new UnsupportedOperationException("Unsupported Temporal type");
                    };
                }

                if (field.getType().isEnum() && value != null) {
                    @SuppressWarnings("rawtypes")
                    Class<? extends Enum> enumType = (Class<? extends Enum>) field.getType();
                    value = Enum.valueOf(enumType, value.toString());
                }

                if (field.getType() == byte[].class && value instanceof Blob blob) {
                    if (blob.length() == 0) {
                        field.set(entityInstance, new byte[0]);
                    } else {
                        field.set(entityInstance, blob.getBytes(1, (int) blob.length()));
                    }
                    continue;
                }

                field.set(entityInstance, value);
            }
        }

        context.getDependencyContainer().getLifecycleManager().invokeOnLoad(entityInstance);
        return entityInstance;
    }

    /**
     * Fetches a foreign entity based on its primary key.
     */
    protected Object fetchForeignEntity(RepositoryInvocationContext<?, ?> context, Connection connection, Class<?> foreignEntityCls, String foreignKeyColumn, Object foreignKeyValue) throws Exception {
        EntityMetadata foreignMetadata = new EntityMetadata(foreignEntityCls, context.getDatabase().getSerializerRegistry());
        String tableName = foreignMetadata.getTableName();
        String pkColumn = foreignMetadata.getPrimaryKeyColumn();

        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(tableName) + " WHERE " + context.getSchemaFormatter().formatColumnName(pkColumn) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            RepositoryUtils.setStatementParameter(statement, 1, foreignKeyValue);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapEntity(context, connection, foreignEntityCls, rs);
                } else {
                    throw new IllegalArgumentException("Foreign entity not found: " + foreignEntityCls.getName() + " WHERE " + pkColumn + " = " + foreignKeyValue);
                }
            }
        }
    }
}
