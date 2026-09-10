package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.ColumnPrefix;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.ForeignKey;
import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.database.Index;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
import net.vortexdevelopment.vinject.database.serializer.SerializerRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EntityMetadata {
    @Getter private final String tableName;
    private final Class<?> clazz;
    @Getter private final List<FieldMetadata> fields = new ArrayList<>();
    @Getter private final List<IndexMetadata> indexes = new ArrayList<>();
    private final Map<String, SerializedFieldInfo> serializedFields = new HashMap<>();

    public EntityMetadata(String tableName, Class<?> clazz) {
        this.tableName = tableName;
        this.clazz = clazz;
    }

    public void resolveFields(Map<Class<?>, EntityMetadata> entityMetadataMap) throws Exception {
        resolveFields(entityMetadataMap, null);
    }

    public void resolveFields(Map<Class<?>, EntityMetadata> entityMetadataMap,
                              SerializerRegistry serializerRegistry) throws Exception {
        fields.clear();
        indexes.clear();
        serializedFields.clear();

        Object entityInstance = clazz.getDeclaredConstructor().newInstance();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (!isPersistent(field)) continue;

            Column column = field.getAnnotation(Column.class);
            Temporal temporal = field.getAnnotation(Temporal.class);
            String columnName = physicalColumnName(field);

            if (serializerRegistry != null && serializerRegistry.hasSerializer(field.getType())) {
                if (field.getAnnotationsByType(Index.class).length > 0 || field.isAnnotationPresent(ForeignKey.class)) {
                    throw new IllegalArgumentException("@Index and @ForeignKey cannot target serialized field "
                            + clazz.getName() + "#" + field.getName());
                }
                addSerializedFields(field, columnName, serializerRegistry);
                continue;
            }

            boolean primaryKey = (column != null && column.primaryKey()) || field.isAnnotationPresent(Id.class);
            boolean nullable = (column != null ? column.nullable() : (temporal == null || temporal.nullable()))
                    && !field.isAnnotationPresent(Id.class);
            boolean unique = column != null && column.unique();
            boolean autoIncrement = (column != null && column.autoIncrement()) || field.isAnnotationPresent(Id.class);

            EntityMetadata entityFieldMetadata = entityMetadataMap.get(field.getType());
            ForeignKey foreignKeyAnnotation = field.getAnnotation(ForeignKey.class);
            ReferencedField referencedField = null;
            Class<?> referencedEntity = null;
            if (entityFieldMetadata != null || foreignKeyAnnotation != null) {
                referencedEntity = resolveReferencedEntity(field, foreignKeyAnnotation);
                if (!entityMetadataMap.containsKey(referencedEntity)) {
                    throw new IllegalArgumentException("Referenced entity is not registered for "
                            + clazz.getName() + "#" + field.getName() + ": " + referencedEntity.getName());
                }
                String requestedColumn = foreignKeyAnnotation == null ? "" : foreignKeyAnnotation.referencedColumn();
                referencedField = resolveReferencedField(referencedEntity, requestedColumn);
                validateCompatibleTypes(field, referencedEntity, referencedField.field());
            }

            Class<?> sqlFieldType = referencedField != null && entityFieldMetadata != null
                    ? referencedField.field().getType() : field.getType();
            String sqlType = Database.getSQLTypeMapper().getSQLType(
                    sqlFieldType, column, temporal, field.get(entityInstance));

            ForeignKeyMetadata foreignKey = null;
            if (foreignKeyAnnotation != null) {
                if (!referencedField.primaryKey() && !referencedField.unique()) {
                    throw new IllegalArgumentException("Foreign key target must be a primary or unique column: "
                            + referencedField.field().getDeclaringClass().getName() + "#" + referencedField.field().getName());
                }
                if ((foreignKeyAnnotation.onDelete() == ForeignKeyAction.SET_NULL
                        || foreignKeyAnnotation.onUpdate() == ForeignKeyAction.SET_NULL) && !nullable) {
                    throw new IllegalArgumentException("SET_NULL requires a nullable local column: "
                            + clazz.getName() + "#" + field.getName());
                }
                EntityMetadata referencedMetadata = entityMetadataMap.get(referencedEntity);
                String constraintName = foreignKeyAnnotation.name().isBlank()
                        ? SchemaNameUtils.foreignKeyName(tableName, columnName,
                        referencedMetadata.getTableName(), referencedField.columnName())
                        : foreignKeyAnnotation.name();
                foreignKey = new ForeignKeyMetadata(
                        constraintName, referencedMetadata.getTableName(), referencedField.columnName(),
                        foreignKeyAnnotation.onDelete(), foreignKeyAnnotation.onUpdate());
            }

            fields.add(new FieldMetadata(
                    field.getName(), columnName, sqlType, primaryKey, nullable, unique, autoIncrement,
                    foreignKey, field, field.get(entityInstance)));
        }

        resolveIndexes();
    }

    private void addSerializedFields(Field field, String columnName, SerializerRegistry serializerRegistry) {
        DatabaseSerializer<?> serializer = serializerRegistry.getSerializer(field.getType());
        boolean usePrefix = field.isAnnotationPresent(ColumnPrefix.class);
        for (FieldMetadata serializedColumn : serializer.getColumns(columnName)) {
            String finalColumnName = usePrefix
                    ? columnName + "_" + serializedColumn.getColumnName()
                    : serializedColumn.getColumnName();
            fields.add(new FieldMetadata(
                    serializedColumn.getFieldName(), finalColumnName, serializedColumn.getSqlType(),
                    serializedColumn.isPrimaryKey(), serializedColumn.isNullable(), serializedColumn.isUnique(),
                    serializedColumn.isAutoIncrement(), serializedColumn.getForeignKey(), serializedColumn.getField(),
                    serializedColumn.getDefaultValue()));
            serializedFields.put(finalColumnName, new SerializedFieldInfo(field, serializer, columnName, usePrefix));
        }
    }

    private void resolveIndexes() {
        Set<String> names = new HashSet<>();
        for (Field field : clazz.getDeclaredFields()) {
            for (Index index : field.getAnnotationsByType(Index.class)) {
                if (index.columns().length != 0) {
                    throw new IllegalArgumentException("Field-level @Index must not declare columns: "
                            + clazz.getName() + "#" + field.getName());
                }
                addIndex(index, List.of(resolveLocalColumn(field.getName())), names);
            }
        }

        for (Index index : clazz.getAnnotationsByType(Index.class)) {
            if (index.columns().length == 0) {
                throw new IllegalArgumentException("Class-level @Index must declare at least one column: " + clazz.getName());
            }
            List<String> columns = new ArrayList<>();
            Set<String> seenColumns = new HashSet<>();
            for (String requested : index.columns()) {
                String resolved = resolveLocalColumn(requested);
                if (!seenColumns.add(resolved.toLowerCase())) {
                    throw new IllegalArgumentException("Duplicate column '" + requested + "' in @Index on " + clazz.getName());
                }
                columns.add(resolved);
            }
            addIndex(index, columns, names);
        }
    }

    private void addIndex(Index annotation, List<String> columns, Set<String> names) {
        String name = annotation.name().isBlank()
                ? SchemaNameUtils.indexName(tableName, columns, annotation.unique())
                : annotation.name();
        if (!names.add(name.toLowerCase())) {
            throw new IllegalArgumentException("Duplicate index name '" + name + "' on " + clazz.getName());
        }
        indexes.add(new IndexMetadata(name, columns, annotation.unique()));
    }

    private String resolveLocalColumn(String requestedName) {
        for (FieldMetadata metadata : fields) {
            if (metadata.getFieldName().equals(requestedName) || metadata.getColumnName().equals(requestedName)) {
                return metadata.getColumnName();
            }
        }
        throw new IllegalArgumentException("Unknown field or column '" + requestedName + "' in @Index on " + clazz.getName());
    }

    private static Class<?> resolveReferencedEntity(Field field, ForeignKey foreignKey) {
        if (foreignKey != null && foreignKey.entity() != void.class) return foreignKey.entity();
        if (field.getType().isAnnotationPresent(Entity.class)) return field.getType();
        throw new IllegalArgumentException("Scalar foreign key field must declare entity: "
                + field.getDeclaringClass().getName() + "#" + field.getName());
    }

    private static ReferencedField resolveReferencedField(Class<?> entityClass, String requestedName) {
        ReferencedField primaryKey = null;
        for (Field field : entityClass.getDeclaredFields()) {
            if (!isPersistent(field)) continue;
            Column column = field.getAnnotation(Column.class);
            boolean isPrimaryKey = field.isAnnotationPresent(Id.class) || (column != null && column.primaryKey());
            boolean isUnique = column != null && column.unique();
            String physicalName = physicalColumnName(field);
            ReferencedField candidate = new ReferencedField(field, physicalName, isPrimaryKey, isUnique);
            if (requestedName != null && !requestedName.isBlank()
                    && (field.getName().equals(requestedName) || physicalName.equals(requestedName))) {
                return candidate;
            }
            if (isPrimaryKey) primaryKey = candidate;
        }
        if (requestedName == null || requestedName.isBlank()) {
            if (primaryKey != null) return primaryKey;
            throw new IllegalArgumentException("Referenced entity does not have a primary key: " + entityClass.getName());
        }
        throw new IllegalArgumentException("Unknown referenced field or column '" + requestedName
                + "' on entity " + entityClass.getName());
    }

    private static void validateCompatibleTypes(Field localField, Class<?> referencedEntity, Field referencedField) {
        if (localField.getType().equals(referencedEntity)) return;
        if (!boxed(localField.getType()).equals(boxed(referencedField.getType()))) {
            throw new IllegalArgumentException("Foreign key type mismatch: " + localField.getDeclaringClass().getName()
                    + "#" + localField.getName() + " is " + localField.getType().getName()
                    + " but references " + referencedField.getDeclaringClass().getName() + "#"
                    + referencedField.getName() + " of type " + referencedField.getType().getName());
        }
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static boolean isPersistent(Field field) {
        return field.isAnnotationPresent(Column.class)
                || field.isAnnotationPresent(Temporal.class)
                || field.isAnnotationPresent(Id.class);
    }

    private static String physicalColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        Temporal temporal = field.getAnnotation(Temporal.class);
        if (column != null && !column.name().isEmpty()) return column.name();
        if (temporal != null && !temporal.name().isEmpty()) return temporal.name();
        return field.getName();
    }

    public FieldMetadata getPrimaryKeyField() {
        return fields.stream().filter(FieldMetadata::isPrimaryKey).findFirst().orElse(null);
    }

    public boolean isSerializedColumn(String columnName) {
        return serializedFields.containsKey(columnName);
    }

    public SerializedFieldInfo getSerializedFieldInfo(String columnName) {
        return serializedFields.get(columnName);
    }

    public java.util.Set<String> getSerializedFieldNames() {
        return serializedFields.values().stream()
                .map(info -> info.getOriginalField().getName())
                .collect(java.util.stream.Collectors.toSet());
    }

    public java.util.List<String> getSerializedColumnNames(String fieldName) {
        java.util.List<String> columnNames = new ArrayList<>();
        for (Map.Entry<String, SerializedFieldInfo> entry : serializedFields.entrySet()) {
            if (entry.getValue().getOriginalField().getName().equals(fieldName)) columnNames.add(entry.getKey());
        }
        return columnNames;
    }

    private record ReferencedField(Field field, String columnName, boolean primaryKey, boolean unique) {
    }

    public static class SerializedFieldInfo {
        private final Field originalField;
        private final DatabaseSerializer<?> serializer;
        private final String baseColumnName;
        private final boolean usePrefix;

        public SerializedFieldInfo(Field originalField, DatabaseSerializer<?> serializer,
                                   String baseColumnName, boolean usePrefix) {
            this.originalField = originalField;
            this.serializer = serializer;
            this.baseColumnName = baseColumnName;
            this.usePrefix = usePrefix;
        }

        public Field getOriginalField() { return originalField; }

        @SuppressWarnings("unchecked")
        public <T> DatabaseSerializer<T> getSerializer() { return (DatabaseSerializer<T>) serializer; }

        public String getBaseColumnName() { return baseColumnName; }

        public boolean isUsePrefix() { return usePrefix; }
    }
}
