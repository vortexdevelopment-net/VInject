package net.vortexdevelopment.vinject.database.repository;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.ColumnPrefix;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
import net.vortexdevelopment.vinject.database.serializer.SerializerRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents metadata of an entity to avoid repeated reflection.
 */
public class EntityMetadata {

    @Getter private final String baseTableName; // Table name without prefix
    @Getter private final Map<String, String> fieldToColumnMap = new HashMap<>();
    @Getter private final String primaryKeyColumn;
    @Getter private final Field primaryKeyField;
    private final Map<String, Field> fieldsMap = new HashMap<>();
    private final Map<String, SerializedFieldInfo> serializedFields = new HashMap<>(); // columnName -> info

    public EntityMetadata(Class<?> entityClass, SerializerRegistry serializerRegistry) {
        Entity entity = entityClass.getAnnotation(Entity.class);
        if (entity == null) {
            throw new IllegalArgumentException("Entity annotation not found on class: " + entityClass.getName());
        }
        this.baseTableName = entity.table().isEmpty() ? entityClass.getSimpleName().toLowerCase() : entity.table();

        String pkColumn = null;
        Field pkField = null;

        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);
            String columnName = field.getName();
            if (field.isAnnotationPresent(Id.class)) {
                pkColumn = columnName;
                pkField = field;
            } else if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                if (!column.name().isEmpty()) {
                    columnName = column.name();
                }
                if (column.primaryKey()) {
                    pkColumn = columnName;
                    pkField = field;
                }
            }

            if (field.isAnnotationPresent(Temporal.class)) {
                Temporal temporal = field.getAnnotation(Temporal.class);
                if (!temporal.name().isEmpty()) {
                    columnName = temporal.name();
                }
            }

            // Check if this field type has a serializer
            if (serializerRegistry != null && serializerRegistry.hasSerializer(field.getType())) {
                DatabaseSerializer<?> serializer = serializerRegistry.getSerializer(field.getType());
                List<net.vortexdevelopment.vinject.database.meta.FieldMetadata> serializedColumns = serializer.getColumns(columnName);
                boolean usePrefix = field.isAnnotationPresent(ColumnPrefix.class);

                for (net.vortexdevelopment.vinject.database.meta.FieldMetadata serializedColumn : serializedColumns) {
                    String finalColumnName = usePrefix
                            ? columnName + "_" + serializedColumn.getColumnName()
                            : serializedColumn.getColumnName();
                    serializedFields.put(finalColumnName, new SerializedFieldInfo(field, serializer, columnName, usePrefix));
                    fieldToColumnMap.put(finalColumnName, finalColumnName);
                }
                fieldsMap.put(field.getName(), field);
                continue;
            }

            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Temporal.class) || field.isAnnotationPresent(Id.class)) {
                fieldToColumnMap.put(field.getName(), columnName);
            }
            fieldsMap.put(field.getName(), field);
        }

        if (pkColumn == null) {
            throw new IllegalArgumentException("No primary key defined in entity: " + entityClass.getName());
        }
        this.primaryKeyColumn = pkColumn;
        this.primaryKeyField = pkField;
    }

    public String getTableName() {
        return Database.getTablePrefix() + baseTableName;
    }

    public String getColumnName(String fieldName) {
        return fieldToColumnMap.get(fieldName);
    }

    public Field getField(String fieldName) {
        return fieldsMap.get(fieldName);
    }

    public boolean isSerializedColumn(String columnName) {
        return serializedFields.containsKey(columnName);
    }

    public SerializedFieldInfo getSerializedFieldInfo(String columnName) {
        return serializedFields.get(columnName);
    }

    public Set<String> getSerializedFieldNames() {
        return serializedFields.values().stream()
                .map(info -> info.originalField().getName())
                .collect(Collectors.toSet());
    }

    public List<String> getSerializedColumnNames(String fieldName) {
        List<String> columnNames = new ArrayList<>();
        for (Map.Entry<String, SerializedFieldInfo> entry : serializedFields.entrySet()) {
            if (entry.getValue().originalField().getName().equals(fieldName)) {
                columnNames.add(entry.getKey());
            }
        }
        return columnNames;
    }
}
