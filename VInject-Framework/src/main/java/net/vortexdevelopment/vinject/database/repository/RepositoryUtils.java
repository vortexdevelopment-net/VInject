package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for Repository operations, providing data conversion and JDBC helpers.
 */
public class RepositoryUtils {

    /**
     * Converts a value to match the target field type.
     *
     * @param value      The value to convert.
     * @param targetType The target field type.
     * @return The converted value.
     */
    public static Object convertValueToFieldType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if (value instanceof Number number) {
            if (targetType == Long.class || targetType == long.class) return number.longValue();
            if (targetType == Integer.class || targetType == int.class) return number.intValue();
            if (targetType == Double.class || targetType == double.class) return number.doubleValue();
            if (targetType == Float.class || targetType == float.class) return number.floatValue();
            if (targetType == Short.class || targetType == short.class) return number.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return number.byteValue();
            if (targetType == BigInteger.class) {
                if (number instanceof BigInteger) return number;
                if (number instanceof BigDecimal) return ((BigDecimal) number).toBigInteger();
                return BigInteger.valueOf(number.longValue());
            }
            if (targetType == BigDecimal.class) {
                if (number instanceof BigDecimal) return number;
                if (number instanceof BigInteger) return new BigDecimal((BigInteger) number);
                return BigDecimal.valueOf(number.doubleValue());
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return number.intValue() != 0;
            }
        }

        if (targetType == UUID.class && value instanceof String) {
            return UUID.fromString((String) value);
        }

        return value;
    }

    /**
     * Extracts and converts a ResultSet cell to the required Java type.
     *
     * @param rs         The ResultSet to read from.
     * @param col        The column index (1-based).
     * @param targetType The desired Java type.
     * @return The extracted and converted value.
     * @throws Exception If an error occurs during extraction or conversion.
     */
    public static Object readResultSetValue(ResultSet rs, int col, Class<?> targetType) throws Exception {
        if (targetType == null || targetType == Object.class) return rs.getObject(col);
        if (targetType == String.class) return rs.getString(col);
        if (targetType == Integer.class || targetType == int.class) return rs.getInt(col);
        if (targetType == Long.class || targetType == long.class) return rs.getLong(col);
        if (targetType == Double.class || targetType == double.class) return rs.getDouble(col);
        if (targetType == Float.class || targetType == float.class) return rs.getFloat(col);
        if (targetType == Boolean.class || targetType == boolean.class) return rs.getBoolean(col);
        if (targetType == Byte.class || targetType == byte.class) return rs.getByte(col);
        if (targetType == Byte[].class) {
            byte[] primitive = rs.getBytes(col);
            if (primitive == null) return null;
            Byte[] wrapper = new Byte[primitive.length];
            for (int i = 0; i < primitive.length; i++) wrapper[i] = primitive[i];
            return wrapper;
        }
        if (targetType == byte[].class) return rs.getBytes(col);
        if (targetType == java.sql.Date.class) return rs.getDate(col);
        if (targetType == Timestamp.class) return rs.getTimestamp(col);
        return rs.getObject(col, targetType);
    }

    /**
     * Sets parameters for PreparedStatement.
     */
    public static void setStatementParameters(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            setStatementParameter(statement, i + 1, values.get(i));
        }
    }

    /**
     * Sets a single parameter for PreparedStatement with type handling.
     */
    public static void setStatementParameter(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value instanceof Timestamp timestamp) {
            statement.setTimestamp(index, timestamp);
        } else if (value instanceof Date date) {
            statement.setDate(index, new java.sql.Date(date.getTime()));
        } else if (value instanceof Enum<?> anEnum) {
            statement.setString(index, anEnum.name());
        } else if (value instanceof UUID uuid) {
            statement.setString(index, uuid.toString());
        } else {
            statement.setObject(index, value);
        }
    }

    /**
     * Unboxes Byte[] to byte[].
     */
    public static byte[] toPrimitiveByteArray(Byte[] bytes) {
        if (bytes == null) return null;
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[i];
        }
        return result;
    }

    /**
     * Fetches the value of the field, or its primary key if it's an entity.
     *
     * @param value   The object to unwrap.
     * @param context The repository invocation context.
     * @return The unwrapped ID or the original value.
     */
    public static Object unwrapEntityId(Object value, RepositoryInvocationContext<?, ?> context) {
        if (value == null) {
            return null;
        }
        
        Class<?> valueClass = value.getClass();
        if (valueClass.isAnnotationPresent(Entity.class)) {
            try {
                // Optimized path if it's the target entity class
                if (valueClass.equals(context.getEntityClass())) {
                    return context.getEntityMetadata().getPrimaryKeyField().get(value);
                }

                // Fallback for different entity types - using cached field lookup
                Field pkField = findPrimaryKeyField(valueClass);
                if (pkField != null) {
                    return pkField.get(value);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    private static final Map<Class<?>, Field> PRIMARY_KEY_CACHE = new ConcurrentHashMap<>();

    public static Field findPrimaryKeyField(Class<?> clazz) {
        return PRIMARY_KEY_CACHE.computeIfAbsent(clazz, key -> {
            Class<?> current = key;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    if (f.isAnnotationPresent(Id.class)) {
                        f.setAccessible(true);
                        return f;
                    } else if (f.isAnnotationPresent(Column.class)) {
                        if (f.getAnnotation(Column.class).primaryKey()) {
                            f.setAccessible(true);
                            return f;
                        }
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        });
    }

    /**
 * Checks if a field is auto-generated (e.g., auto-incremented primary key).
 * Only numeric types (Integer, Long) with @Id are treated as auto-generated.
 * UUIDs and other types must be explicitly set by the application.
 *
 * @param field The field to check.
 * @return True if auto-generated.
 */
public static boolean isAutoGenerated(Field field) {
    // Explicit autoIncrement flag takes precedence
    if (field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).autoIncrement()) {
        return true;
    }
    
    // @Id annotation only implies auto-generation for numeric types
    if (field.isAnnotationPresent(Id.class)) {
        Class<?> type = field.getType();
        return type == Integer.class || type == int.class || 
               type == Long.class || type == long.class ||
               type == BigInteger.class ||
               type == Short.class || type == short.class ||
               type == Byte.class || type == byte.class;
    }
    
    return false;
}
}