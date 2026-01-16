package net.vortexdevelopment.vinject.database.mapper;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
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

        if (column == null && temporal == null) {
            // This is likely an @Id field - create a synthetic column with sensible defaults
            // @Id fields are: primaryKey=true, autoIncrement=true, nullable=false
            return getSQLTypeForIdField(fieldType);
        }

        if (column == null) {
            throw new IllegalArgumentException("Column annotation is required for field: " + fieldType.getName() + ". If it is a Date/Time field, use Temporal annotation instead.");
        }

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
            case "Float", "float" -> {
                // H2 doesn't support FLOAT(precision, scale), use DECIMAL instead when precision/scale are specified
                // or REAL/FLOAT when they're not
                if (column.precision() != -1 || column.scale() != -1 || column.length() != -1) {
                    int p = column.precision() != -1 ? column.precision() : (column.length() != -1 ? column.length() : 10);
                    int s = column.scale() != -1 ? column.scale() : 2;
                    yield "DECIMAL(" + p + "," + s + ")";
                } else {
                    yield "REAL";
                }
            }
            case "Boolean", "boolean" -> "TINYINT";
            case "Date" -> "DATETIME";
            case "Byte[]", "byte[]" -> (column.length() != -1 && column.length() != 255) ? "BINARY VARYING(" + column.length() + ")" : "BLOB";
            case "UUID" -> "UUID";
            case "BigDecimal" -> {
                int p = column.precision() != -1 ? column.precision() : (column.length() != -1 ? column.length() : 10);
                int s = column.scale() != -1 ? column.scale() : 2;
                yield "NUMERIC(" + p + "," + s + ")";
            }
            case "BigInteger" -> {
                int p = column.precision() != -1 ? column.precision() : (column.length() != -1 ? column.length() : 38);
                yield "NUMERIC(" + p + ",0)";
            }
            default -> throw new UnsupportedOperationException("Unsupported field type: " + fieldType.getName());
        } + nullableConstraint;
    }

    private String getSQLTypeForIdField(Class<?> fieldType) {
        // For @Id fields, we don't have nullable constraint (they're always NOT NULL and AUTO_INCREMENT for numeric types)
        String notNullIdentity = " NOT NULL AUTO_INCREMENT";
        String notNull = " NOT NULL";

        return switch (fieldType.getSimpleName()) {
            case "String" -> "CHARACTER VARYING(255)" + notNull;
            case "Integer", "int" -> "INTEGER" + notNullIdentity;
            case "Long", "long" -> "BIGINT" + notNullIdentity;
            case "BigInteger" -> "BIGINT" + notNullIdentity;
            case "UUID" -> "UUID" + notNull;
            default -> throw new UnsupportedOperationException("Unsupported @Id field type: " + fieldType.getName());
        };
    }
}
