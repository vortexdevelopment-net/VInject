package net.vortexdevelopment.vinject.database;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;

public class SQLDataTypeMapper {

    public static String getSQLType(Class<?> fieldType, Column column, Temporal temporal) {
        if (temporal != null) {
            // Handle temporal types if needed
            return switch (temporal.value()) {
                case DATE -> "DATE" + (temporal.nullable() ? "" : " NOT NULL");
                case TIME -> "TIME" + (temporal.nullable() ? "" : " NOT NULL");
                case TIMESTAMP -> "TIMESTAMP"
                        + (temporal.nullable() ? " DEFAULT " + (temporal.nullDefault() ? "NULL" : "current_timestamp()") : " NOT NULL DEFAULT current_timestamp()")
                        + (temporal.currentTimestampOnUpdate() ? " ON UPDATE current_timestamp()" : "");
                default -> throw new UnsupportedOperationException("Unsupported Temporal type");
            };
        }

        if (column == null) {
            throw new IllegalArgumentException("Column annotation is required for field: " + fieldType.getName() + ". If it is a Date/Time field, use Temporal annotation instead.");
        }

        boolean primaryKey = column.primaryKey();
        boolean autoIncrement = column.autoIncrement();

        //When a column auto increment it cannot be nullable
        String nullableConstraint = column.nullable() && !autoIncrement && !primaryKey ? " DEFAULT NULL" : "";


        return switch (fieldType.getSimpleName()) {
            case "String" -> "VARCHAR(" + (column.length() != -1 ? column.length() : 255) + ")" + nullableConstraint;
            case "Integer", "int" -> "INT(11)" + nullableConstraint;
            case "Long", "long" -> "BIGINT(20)" + nullableConstraint;
            case "Double", "double" -> "DOUBLE PRECISION" + nullableConstraint;
            case "Float", "float" -> "FLOAT(" + (column.precision() != -1 ? column.precision() : 10) + ","
                    + (column.scale() != -1 ? column.scale() : 2) + ")" + nullableConstraint;
            case "Boolean", "boolean" -> "BOOLEAN" + nullableConstraint;
            case "Date" -> "DATETIME" + nullableConstraint;
            case "byte[]" -> "BLOB" + nullableConstraint;
            case "UUID" -> "UUID" + nullableConstraint;
            default -> throw new UnsupportedOperationException("Unsupported field type: " + fieldType.getName());
        };
    }
}
