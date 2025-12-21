package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.ColumnPrefix;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
import net.vortexdevelopment.vinject.database.serializer.SerializerRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityMetadata {
    @Getter
    private final String tableName;
    private final Class<?> clazz;
    @Getter
    private final List<FieldMetadata> fields = new ArrayList<>();
    // Map from serialized field name to the original field and serializer
    private final Map<String, SerializedFieldInfo> serializedFields = new HashMap<>();

    public EntityMetadata(String tableName, Class<?> clazz) {
        this.tableName = tableName;
        this.clazz = clazz;
    }

    public void resolveFields(Map<Class<?>, EntityMetadata> entityMetadataMap) throws Exception {
        resolveFields(entityMetadataMap, null);
    }

    public void resolveFields(Map<Class<?>, EntityMetadata> entityMetadataMap, SerializerRegistry serializerRegistry) throws Exception {
        //Debug print entityMetadataMap

        Object entityInstance = clazz.getDeclaredConstructor().newInstance();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Temporal.class)) {
                Column column = field.getAnnotation(Column.class);
                Temporal temporal = field.getAnnotation(Temporal.class);
                String columnName = (column != null && !column.name().isEmpty()) ? column.name() : temporal != null ? temporal.name() : field.getName();
                
                // Check if this field type has a serializer
                if (serializerRegistry != null && serializerRegistry.hasSerializer(field.getType())) {
                    // Handle serialized field - create multiple columns
                    DatabaseSerializer<?> serializer = serializerRegistry.getSerializer(field.getType());
                    List<FieldMetadata> serializedColumns = serializer.getColumns(columnName);
                    boolean usePrefix = field.isAnnotationPresent(ColumnPrefix.class);
                    
                    // Store mapping from serialized columns back to original field
                    for (FieldMetadata serializedColumn : serializedColumns) {
                        // Conditionally prefix column name based on @ColumnPrefix annotation
                        String finalColumnName = usePrefix 
                            ? columnName + "_" + serializedColumn.getColumnName()
                            : serializedColumn.getColumnName();
                        
                        // Create a new FieldMetadata with the potentially prefixed column name
                        FieldMetadata prefixedColumn = new FieldMetadata(
                            serializedColumn.getFieldName(),
                            finalColumnName,
                            serializedColumn.getSqlType(),
                            serializedColumn.isPrimaryKey(),
                            serializedColumn.isNullable(),
                            serializedColumn.isUnique(),
                            serializedColumn.isAutoIncrement(),
                            serializedColumn.getForeignKey(),
                            serializedColumn.getField(),
                            serializedColumn.getDefaultValue()
                        );
                        
                        serializedFields.put(finalColumnName, new SerializedFieldInfo(field, serializer, columnName, usePrefix));
                        fields.add(prefixedColumn);
                    }
                    continue;
                }
                
                boolean isPrimaryKey = column != null && column.primaryKey();
                boolean isNullable = column == null || column.nullable();
                boolean isUnique = column != null && column.unique();
                boolean isAutoIncrement = column != null && column.autoIncrement();

                String sqlType;
                ForeignKeyMetadata foreignKey = null;

                if (isEntityField(field, entityMetadataMap)) {
                    // Handle foreign key
                    Class<?> relatedClass = field.getType();
                    EntityMetadata relatedMetadata = entityMetadataMap.get(relatedClass);
                    if (relatedMetadata == null) {
                        throw new RuntimeException("Referenced entity not found for field: " + field.getName());
                    }
                    FieldMetadata pkField = relatedMetadata.getPrimaryKeyField();
                    if (pkField == null) {
                        //Try to load manually
                        Field[] fields = relatedClass.getDeclaredFields();
                        for (Field f : fields) {
                            if (f.isAnnotationPresent(Column.class)) {
                                Column c = f.getAnnotation(Column.class);
                                if (c.primaryKey()) {
                                    pkField = new FieldMetadata(f.getName(), c.name().isEmpty() ? f.getName() : c.name(), Database.getSQLTypeMapper().getSQLType(f.getType(), c, null, null), true, c.nullable(), c.unique(), c.autoIncrement(), null, f, null);
                                    break;
                                }
                            }
                        }

                        if (pkField == null) {
                            throw new RuntimeException("Referenced entity does not have a primary key: " + relatedClass.getName());
                        }
                    }
                    sqlType = pkField.getSqlType();
                    foreignKey = new ForeignKeyMetadata(relatedMetadata.getTableName(), pkField.getColumnName());
                } else {
                    // Regular column
                    sqlType = Database.getSQLTypeMapper().getSQLType(field.getType(), column, temporal, field.get(entityInstance));
                }

                FieldMetadata fieldMeta = new FieldMetadata(
                        field.getName(),
                        columnName,
                        sqlType,
                        isPrimaryKey,
                        isNullable,
                        isUnique,
                        isAutoIncrement,
                        foreignKey,
                        field,
                        field.get(entityInstance)
                );

                fields.add(fieldMeta);
            }
        }
    }

    private boolean isEntityField(Field field, Map<Class<?>, EntityMetadata> entityMetadataMap) {
        return entityMetadataMap.containsKey(field.getType());
    }

    public FieldMetadata getPrimaryKeyField() {
        for (FieldMetadata fieldMeta : fields) {
            if (fieldMeta.isPrimaryKey()) {
                return fieldMeta;
            }
        }
        return null;
    }

    /**
     * Check if a column name belongs to a serialized field.
     */
    public boolean isSerializedColumn(String columnName) {
        return serializedFields.containsKey(columnName);
    }

    /**
     * Get information about a serialized field by column name.
     */
    public SerializedFieldInfo getSerializedFieldInfo(String columnName) {
        return serializedFields.get(columnName);
    }

    /**
     * Get all serialized field base names (original field names).
     */
    public java.util.Set<String> getSerializedFieldNames() {
        return serializedFields.values().stream()
            .map(info -> info.getOriginalField().getName())
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all column names for a serialized field.
     */
    public java.util.List<String> getSerializedColumnNames(String fieldName) {
        java.util.List<String> columnNames = new ArrayList<>();
        for (Map.Entry<String, SerializedFieldInfo> entry : serializedFields.entrySet()) {
            if (entry.getValue().getOriginalField().getName().equals(fieldName)) {
                columnNames.add(entry.getKey());
            }
        }
        return columnNames;
    }

    /**
     * Internal class to hold information about a serialized field.
     */
    public static class SerializedFieldInfo {
        private final Field originalField;
        private final DatabaseSerializer<?> serializer;
        private final String baseColumnName;
        private final boolean usePrefix;

        public SerializedFieldInfo(Field originalField, DatabaseSerializer<?> serializer, String baseColumnName, boolean usePrefix) {
            this.originalField = originalField;
            this.serializer = serializer;
            this.baseColumnName = baseColumnName;
            this.usePrefix = usePrefix;
        }

        public Field getOriginalField() {
            return originalField;
        }

        @SuppressWarnings("unchecked")
        public <T> DatabaseSerializer<T> getSerializer() {
            return (DatabaseSerializer<T>) serializer;
        }

        public String getBaseColumnName() {
            return baseColumnName;
        }

        public boolean isUsePrefix() {
            return usePrefix;
        }
    }
}
