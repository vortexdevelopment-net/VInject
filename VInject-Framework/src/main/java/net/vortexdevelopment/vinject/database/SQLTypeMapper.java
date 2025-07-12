package net.vortexdevelopment.vinject.database;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Temporal;

public interface SQLTypeMapper {

    String getSQLType(Class<?> fieldType, Column column, Temporal temporal, Object defaultValue);

    default String getTemporalModifiers(Temporal temporal) {
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

    default String getColumnModifiers(Column column, Object defaultValue, boolean isEnum) {
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
}
