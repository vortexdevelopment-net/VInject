package net.vortexdevelopment.vinject.database;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;

public class SQLDataTypeMapper {

    private static String getTemporalModifiers(Temporal temporal) {
        StringBuilder sb = new StringBuilder();
        if (!temporal.nullable()) {
            sb.append(" NOT NULL");
        } else {
            sb.append(" NULL");
        }
        if (temporal.nullDefault()) {
            sb.append(" DEFAULT NULL");
        } else if (temporal.currentTimestampOnInsert()) {
            sb.append(" DEFAULT CURRENT_TIMESTAMP(3)");
        }
        if (temporal.currentTimestampOnUpdate()) {
            sb.append(" ON UPDATE CURRENT_TIMESTAMP(3)");
        }
        return sb.toString();
    }

    public static String getColumnModifiers(Column column, Object defaultValue, boolean isEnum) {
        StringBuilder sb = new StringBuilder();
        if (!column.nullable() || column.autoIncrement()) {
            sb.append(" NOT NULL");
        } else {
            sb.append(" NULL");
        }

        //Default value
        if (defaultValue != null && !column.autoIncrement()) {
            if (defaultValue instanceof Boolean) {
                sb.append(" DEFAULT ").append((Boolean) defaultValue ? 1 : 0);
            } else if (defaultValue instanceof Number) {
                sb.append(" DEFAULT ").append(defaultValue);
            } else if (defaultValue instanceof String) {
                sb.append(" DEFAULT '").append(defaultValue).append("'");
            } else if (isEnum) {
                sb.append(" DEFAULT '").append(defaultValue.toString()).append("'");
            } else {
                sb.append(" DEFAULT '").append(defaultValue.toString()).append("'");
            }
        } else if (defaultValue == null && !column.autoIncrement()) {
            sb.append(" DEFAULT NULL");
        }

        if (column.unique()) {
            sb.append(" UNIQUE");
        }
        if (column.autoIncrement()) {
            sb.append(" AUTO_INCREMENT");
        }
        return sb.toString();
    }

    public static String getSQLType(Class<?> fieldType, Column column, Temporal temporal, Object defaultValue) {
        if (temporal != null) {
            // Handle temporal types if needed
            return switch (temporal.value()) {
                case DATE -> "DATE(3)" + getTemporalModifiers(temporal);
                case TIME -> "TIME(3)" + getTemporalModifiers(temporal);
                case TIMESTAMP -> "TIMESTAMP(3)" + getTemporalModifiers(temporal);
                default -> throw new UnsupportedOperationException("Unsupported Temporal type");
            };
        }

        if (column == null) {
            throw new IllegalArgumentException("Column annotation is required for field: " + fieldType.getName() + ". If it is a Date/Time field, use Temporal annotation instead.");
        }

        boolean primaryKey = column.primaryKey();
        boolean autoIncrement = column.autoIncrement();

        //When a column auto increment it cannot be nullable
        //String nullableConstraint = column.nullable() && !autoIncrement && !primaryKey ? " DEFAULT NULL" : "";
        String nullableConstraint = getColumnModifiers(column, defaultValue, fieldType.isEnum()); //defaultValue != null && !autoIncrement && !primaryKey ? " NOT NULL DEFAULT '" + defaultValue+"'" : (column.nullable() && !autoIncrement && !primaryKey ? " NULL" : " NOT NULL");

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
            case "String" -> "VARCHAR(" + (column.length() != -1 ? column.length() : 255) + ")" + nullableConstraint;
            case "Integer", "int" -> "INT(11)" + nullableConstraint;
            case "Long", "long" -> "BIGINT(20)" + nullableConstraint;
            case "Double", "double" -> "DOUBLE" + nullableConstraint;
            case "Float", "float" -> "FLOAT(" + (column.precision() != -1 ? column.precision() : 10) + ","
                    + (column.scale() != -1 ? column.scale() : 2) + ")" + nullableConstraint;
            case "Boolean", "boolean" -> "TINYINT(1)" + nullableConstraint;
            case "Date" -> "DATETIME" + nullableConstraint;
            case "byte[]" -> "BLOB" + nullableConstraint;
            case "UUID" -> "UUID" + nullableConstraint;
            case "BigDecimal" -> "DECIMAL(" + (column.precision() != -1 ? column.precision() : 10) + ","
                    + (column.scale() != -1 ? column.scale() : 2) + ")" + nullableConstraint;
            default -> throw new UnsupportedOperationException("Unsupported field type: " + fieldType.getName());
        };
    }
}
