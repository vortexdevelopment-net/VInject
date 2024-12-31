package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;

import java.lang.reflect.Field;

@Getter
public class FieldMetadata {
    private final String fieldName;
    private final String columnName;
    private final String sqlType;
    private final boolean primaryKey;
    private final boolean nullable;
    private final boolean unique;
    private final boolean autoIncrement;
    private final ForeignKeyMetadata foreignKey;
    private final Field field;
    private final Object defaultValue;

    public FieldMetadata(String fieldName, String columnName, String sqlType, boolean primaryKey,
                         boolean nullable, boolean unique, boolean autoIncrement,
                         ForeignKeyMetadata foreignKey, Field field, Object defaultValue) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.sqlType = sqlType;
        this.primaryKey = primaryKey;
        this.nullable = nullable;
        this.unique = unique;
        this.autoIncrement = autoIncrement;
        this.foreignKey = foreignKey;
        this.field = field;
        this.defaultValue = defaultValue;
    }

    public boolean isForeignKey() {
        return foreignKey != null;
    }

    public boolean isEnum() {
        return field.getType().isEnum();
    }

    public boolean hasDefaultValue() {
        return defaultValue != null;
    }
}
