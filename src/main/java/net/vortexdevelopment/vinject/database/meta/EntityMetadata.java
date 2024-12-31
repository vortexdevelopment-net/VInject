package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.SQLDataTypeMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityMetadata {
    @Getter
    private final String tableName;
    private final Class<?> clazz;
    @Getter
    private final List<FieldMetadata> fields = new ArrayList<>();

    public EntityMetadata(String tableName, Class<?> clazz) {
        this.tableName = tableName;
        this.clazz = clazz;
    }

    public void resolveFields(Map<Class<?>, EntityMetadata> entityMetadataMap) throws Exception {
        //Debug print entityMetadataMap

        Object entityInstance = clazz.getDeclaredConstructor().newInstance();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Temporal.class)) {
                Column column = field.getAnnotation(Column.class);
                Temporal temporal = field.getAnnotation(Temporal.class);
                String columnName = (column != null && !column.name().isEmpty()) ? column.name() : temporal != null ? temporal.name() : field.getName();
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
                                    pkField = new FieldMetadata(f.getName(), c.name().isEmpty() ? f.getName() : c.name(), SQLDataTypeMapper.getSQLType(f.getType(), c, null, null), true, c.nullable(), c.unique(), c.autoIncrement(), null, f, null);
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
                    sqlType = SQLDataTypeMapper.getSQLType(field.getType(), column, temporal, field.get(entityInstance));
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
}
