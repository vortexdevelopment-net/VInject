package net.vortexdevelopment.vinject.database.mapper;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.SQLTypeMapper;

public class MySQLMapper implements SQLTypeMapper {
    @Override
    public String getSQLType(Class<?> fieldType, Column column, Temporal temporal, Object defaultValue) {
        if (temporal != null) {
            // Handle temporal types if needed
            return switch (temporal.value()) {
                case DATE -> "DATE(3)" + getTemporalModifiers(temporal);
                case TIME -> "TIME(3)" + getTemporalModifiers(temporal);
                case TIMESTAMP -> "TIMESTAMP(3)" + getTemporalModifiers(temporal);
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

        return switch (fieldType.getSimpleName()) {
            case "String" -> "VARCHAR(" + (column.length() != -1 ? column.length() : 255) + ")";
            case "Integer", "int" -> "INT(11)";
            case "Long", "long" -> "BIGINT(20)";
            case "Double", "double" -> "DOUBLE";
            case "Float", "float" -> {
                int p = column.precision() != -1 ? column.precision() : (column.length() != -1 ? column.length() : 10);
                int s = column.scale() != -1 ? column.scale() : 2;
                yield "FLOAT(" + p + "," + s + ")";
            }
            case "Boolean", "boolean" -> "TINYINT(1)";
            case "Date" -> "DATETIME";
            case "Byte[]", "byte[]" -> "BLOB";
            case "UUID" -> "UUID";
            case "BigDecimal" -> {
                int p = column.precision() != -1 ? column.precision() : (column.length() != -1 ? column.length() : 10);
                int s = column.scale() != -1 ? column.scale() : 2;
                yield "DECIMAL(" + p + "," + s + ")";
            }
            case "BigInteger" -> {
                // MariaDB/MySQL doesn't support AUTO_INCREMENT on DECIMAL, use BIGINT instead
                if (column.autoIncrement()) {
                    yield "BIGINT(20)";
                } else {
                    int p = column.precision() != -1 ? column.precision() : (column.length() != -1 ? column.length() : 38);
                    yield "DECIMAL(" + p + ",0)";
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported field type: " + fieldType.getName());
        } + nullableConstraint;
    }

    private String getSQLTypeForIdField(Class<?> fieldType) {
        // For @Id fields, we don't have nullable constraint (they're always NOT NULL and AUTO_INCREMENT for numeric types)
        String notNullAutoIncrement = " NOT NULL AUTO_INCREMENT";
        String notNull = " NOT NULL";

        return switch (fieldType.getSimpleName()) {
            case "String" -> "VARCHAR(255)" + notNull;
            case "Integer", "int" -> "INT(11)" + notNullAutoIncrement;
            case "Long", "long" -> "BIGINT(20)" + notNullAutoIncrement;
            case "BigInteger" -> "BIGINT(20)" + notNullAutoIncrement;
            case "UUID" -> "UUID" + notNull;
            default -> throw new UnsupportedOperationException("Unsupported @Id field type: " + fieldType.getName());
        };
    }
}
