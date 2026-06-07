package net.vortexdevelopment.vinject.database.serializer;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.FieldValue;
import net.vortexdevelopment.vinject.annotation.database.MethodValue;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.meta.FieldMetadata;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for database serializers.
 * Helper classes extend this and define fields with @Column annotations.
 * The system automatically handles serialization/deserialization based on field definitions.
 * 
 * @param <T> The type being serialized (e.g., Location)
 */
public abstract class DatabaseSerializer<T> {

    @Getter
    private final Class<T> targetType;
    private final List<SerializedFieldInfo> serializedFields;

    @SuppressWarnings("unchecked")
    public DatabaseSerializer() {
        // Get the generic type parameter
        Type superclass = this.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType) {
            this.targetType = (Class<T>) ((ParameterizedType) superclass).getActualTypeArguments()[0];
        } else {
            throw new IllegalStateException("RegisterDatabaseSerializer must be parameterized with a type");
        }
        
        // Scan fields annotated with @Column
        this.serializedFields = scanSerializedFields();
    }

    /**
     * Get column definitions for this serializer.
     * Called during EntityMetadata resolution to create database columns.
     * 
     * @param baseColumnName The base column name from the entity field
     * @return List of FieldMetadata entries for each serialized column
     */
    public List<FieldMetadata> getColumns(String baseColumnName) {
        List<FieldMetadata> columns = new ArrayList<>();
        
        for (SerializedFieldInfo fieldInfo : serializedFields) {
            Field field = fieldInfo.field();
            Column columnAnnotation = fieldInfo.columnAnnotation();
            
            // Determine column name
            String columnName;
            if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                columnName = columnAnnotation.name();
            } else {
                columnName = field.getName();
            }
            
            // Get SQL type using SQLTypeMapper
            String sqlType = Database.getSQLTypeMapper().getSQLType(
                field.getType(),
                columnAnnotation,
                null,
                fieldInfo.defaultValue()
            );
            
            // Create FieldMetadata
            FieldMetadata fieldMetadata = new FieldMetadata(
                field.getName(),
                columnName,
                sqlType,
                columnAnnotation != null && columnAnnotation.primaryKey(),
                columnAnnotation == null || columnAnnotation.nullable(),
                columnAnnotation != null && columnAnnotation.unique(),
                columnAnnotation != null && columnAnnotation.autoIncrement(),
                null, // No foreign key for serialized fields
                field,
                fieldInfo.defaultValue()
            );
            
            columns.add(fieldMetadata);
        }
        
        return columns;
    }

    /**
     * Serialize an object to a map of column values.
     * 
     * @param object The object to serialize
     * @return Map of column names (without base prefix) to values
     */
    public Map<String, Object> serialize(T object) {
        Map<String, Object> values = new HashMap<>();
        
        if (object == null) {
            // Return null for all fields
            for (SerializedFieldInfo fieldInfo : serializedFields) {
                String key = getColumnKey(fieldInfo);
                values.put(key, null);
            }
            return values;
        }
        
        for (SerializedFieldInfo fieldInfo : serializedFields) {
            String key = getColumnKey(fieldInfo);
            
            // Extract value using FieldExtractor
            Object value = FieldExtractor.extractValue(
                object,
                fieldInfo.fieldAnnotation(),
                fieldInfo.methodAnnotation()
            );
            
            values.put(key, value);
        }
        
        return values;
    }

    /**
     * Deserialize column values back into an object.
     * Can be overridden for custom deserialization logic.
     * 
     * @param columnValues Map of column names (without base prefix) to values
     * @return The deserialized object, or null if deserialization fails
     */
    public T deserialize(Map<String, Object> columnValues) {
        // Default implementation: try to construct using reflection
        // Subclasses should override this for proper object construction
        try {
            return targetType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the list of serialized field information.
     */
    protected List<SerializedFieldInfo> getSerializedFields() {
        return serializedFields;
    }

    /**
     * Scan all fields annotated with @Column in this class.
     */
    private List<SerializedFieldInfo> scanSerializedFields() {
        List<SerializedFieldInfo> fields = new ArrayList<>();
        
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Column.class)) {
                field.setAccessible(true);
                
                Column columnAnnotation = field.getAnnotation(Column.class);
                FieldValue fieldAnnotation = field.getAnnotation(FieldValue.class);
                MethodValue methodAnnotation = field.getAnnotation(MethodValue.class);
                
                // Get default value if field is initialized
                Object defaultValue = null;
                try {
                    defaultValue = field.get(this);
                } catch (IllegalAccessException ignored) {
                    // FieldValue may not be accessible or initialized
                }
                
                fields.add(new SerializedFieldInfo(
                    field,
                    columnAnnotation,
                    fieldAnnotation,
                    methodAnnotation,
                    defaultValue
                ));
            }
        }
        
        return fields;
    }

    /**
     * Get the column key for a field (used in serialize/deserialize maps).
     */
    private String getColumnKey(SerializedFieldInfo fieldInfo) {
        Column columnAnnotation = fieldInfo.columnAnnotation();
        Field field = fieldInfo.field();
        
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            return columnAnnotation.name();
        }
        return field.getName();
    }
}
