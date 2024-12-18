package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;

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

    public FieldMetadata(String fieldName, String columnName, String sqlType, boolean primaryKey,
                         boolean nullable, boolean unique, boolean autoIncrement,
                         ForeignKeyMetadata foreignKey) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.sqlType = sqlType;
        this.primaryKey = primaryKey;
        this.nullable = nullable;
        this.unique = unique;
        this.autoIncrement = autoIncrement;
        this.foreignKey = foreignKey;
    }

    public boolean isForeignKey() {
        return foreignKey != null;
    }
}
