package net.vortexdevelopment.vinject.database.mapper;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.SQLTypeMapper;

public class H2Mapper implements SQLTypeMapper {

    @Override
    public String getSQLType(Class<?> fieldType, Column column, Temporal temporal, Object defaultValue) {
        if (temporal != null) {
            // Handle temporal types if needed
            return switch (temporal.value()) {
                case DATE -> "DATE(3)" + getTemporalModifiers(temporal);
                case TIME -> "TIME(3)" + getTemporalModifiers(temporal);
                case TIMESTAMP -> "TIMESTAMP" + getTemporalModifiers(temporal);
                default -> throw new UnsupportedOperationException("Unsupported Temporal type");
            };
        }

        if (column == null) {
            throw new IllegalArgumentException("Column annotation is required for field: " + fieldType.getName() + ". If it is a Date/Time field, use Temporal annotation instead.");
        }

        boolean primaryKey = column.primaryKey();

        //When a column auto increment it cannot be nullable
        String nullableConstraint = getColumnModifiers(column, defaultValue, fieldType.isEnum());

        // Handle enum types
        if (fieldType.isEnum()) {
            StringBuilder enumValues = new StringBuilder();
            Object[] constants = fieldType.getEnumConstants();
            for (Object constant : constants) {
                enumValues.append("'").append(constant.toString()).append("',");
            }
            // Remove the trailing comma
            if (!enumValues.isEmpty()) {
                enumValues.deleteCharAt(enumValues.length() - 1);
            }
            return "ENUM(" + enumValues + ")" + nullableConstraint;
        }

        //Type mismatch for column: type Expected: ENUM('ECONOMY','EFFECT','UNKNOWN') NULL DEFAULT NULL Actual: CHARACTER VARYING(255) NULL DEFAULT NULL

        return switch (fieldType.getSimpleName()) {
            case "String" -> "CHARACTER VARYING(" + (column.length() != -1 ? column.length() : 255) + ")";
            case "Integer", "int" -> "INTEGER";
            case "Long", "long" -> "BIGINT";
            case "Double", "double" -> "DOUBLE PRECISION";
            case "Float", "float" -> "FLOAT(" + (column.precision() != -1 ? column.precision() : 10) + ","
                    + (column.scale() != -1 ? column.scale() : 2) + ")";
            case "Boolean", "boolean" -> "TINYINT(1)";
            case "Date" -> "DATETIME";
            case "Byte[]", "byte[]" -> "BLOB";
            case "UUID" -> "UUID";
            case "BigDecimal" -> "DECIMAL(" + (column.precision() != -1 ? column.precision() : 10) + ","
                    + (column.scale() != -1 ? column.scale() : 2) + ")";
            default -> throw new UnsupportedOperationException("Unsupported field type: " + fieldType.getName());
        } + nullableConstraint;
    }
}
