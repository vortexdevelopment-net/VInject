package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Temporal;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.TemporalType;
import net.vortexdevelopment.vinject.database.formatter.SchemaFormatter;
import net.vortexdevelopment.vinject.database.meta.EntityMetadata;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;


import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enhanced InvocationHandler to implement CRUD operations for CrudRepository interface.
 *
 * @param <T>  Entity type
 * @param <ID> ID type
 */
public class RepositoryInvocationHandler<T, ID> implements InvocationHandler {

    private final Class<?> repositoryClass;
    private final Class<T> entityClass;
    private final EntityMetadata entityMetadata;
    private final Database database; // Assume Database is a utility class for DB connections
    private final DependencyContainer dependencyContainer; // For dependency injection
    private final Map<String, String> queryCache = new HashMap<>();
    private final SchemaFormatter schemaFormatter;

    public RepositoryInvocationHandler(Class<?> repositoryClass, Class<T> entityClass, Database database, DependencyContainer dependencyContainer) {
        this.entityClass = entityClass;
        this.entityMetadata = new EntityMetadata(entityClass);
        this.database = database;
        this.dependencyContainer = dependencyContainer;
        this.repositoryClass = repositoryClass;
        this.schemaFormatter = database.getSchemaFormatter();
    }

    @SuppressWarnings("unchecked")
    public CrudRepository<T, ID> create() {
        return (CrudRepository<T, ID>) Proxy.newProxyInstance(
                repositoryClass.getClassLoader(),
                new Class[]{repositoryClass},
                this
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long startTime = System.currentTimeMillis();

        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();

        // Handle CRUD repository methods
        switch (methodName) {
            case "save":
                return save((T) args[0]);
            case "saveAll":
                return saveAll((Iterable<T>) args[0]);
            case "query":
                return handleQuery(method, args, startTime);
            case "findById":
                return findById((ID) args[0]);
            case "existsById":
                return existsById((ID) args[0]);
            case "findAll":
                if (args == null || args.length == 0) {
                    return findAll();
                } else if (args.length == 1 && args[0] instanceof Iterable) {
                    return findAllById((Iterable<ID>) args[0]);
                }
                throw new IllegalArgumentException("Invalid arguments for findAll");
            case "count":
                return count();
            case "deleteById":
                deleteById((ID) args[0]);
                return null;
            case "delete":
                delete((T) args[0]);
                return null;
            case "deleteAllById":
                deleteAllById((Iterable<? extends ID>) args[0]);
                return null;
            case "deleteAll":
                if (args == null || args.length == 0) {
                    deleteAll();
                } else if (args.length == 1 && args[0] instanceof Iterable) {
                    deleteAll((Iterable<? extends T>) args[0]);
                }
                return null;
            default:
                break;
        }

        // If the method didn't match any CrudRepository methods, proceed with dynamic methods
        if (methodName.startsWith("findBy")) {
            return handleFindByMethod(method, args, startTime);
        }

        if (methodName.startsWith("findAllBy")) {
            return handleFindAllByMethod(method, args, startTime);
        }

        if (methodName.startsWith("deleteBy")) {
            return handleDeleteByMethod(method, args, startTime);
        }

        if (methodName.startsWith("deleteAllBy")) {
            return handleDeleteAllByMethod(method, args, startTime);
        }

        //Check if the interface has the default method
        if (method.isDefault()) {
            // Using Java 9+ MethodHandles to invoke the default method in the interface
            final Class<?> declaringClass = method.getDeclaringClass();
            // create a private lookup for the declaring interface
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
            // unreflect the special method handle for the default method
            MethodHandle handle = lookup.findSpecial(
                    declaringClass,
                    method.getName(),
                    MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                    declaringClass);
            // invoke with proxy as the first argument (this/self), followed by args
            return handle.bindTo(proxy).invokeWithArguments(args == null ? new Object[0] : args);
        }


        // Handle basic Object methods
        return handleObjectMethods(proxy, method, args);
    }

    //It does not map to an entity
    private Object handleQuery(Method method, Object[] args, long startTime) throws Exception {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException("Query string and result type must be provided!");
        }
        String sql = (String) args[0];
        Class<?> returnType = (Class<?>) args[1];

        int paramCount = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') {
                paramCount++;
            }
        }
        int passedParams = args.length - 2; // skip SQL and returnType
        if (paramCount != passedParams) {
            throw new IllegalArgumentException("Mismatch between query parameters and arguments. Query: " + sql +
                    ", Parameters: " + passedParams + ", Expected: " + paramCount);
        }

        Object[] params;
        if (args.length == 3 && args[2] instanceof Object[]) {
            params = (Object[]) args[2];
        } else if (args.length > 2) {
            params = Arrays.copyOfRange(args, 2, args.length);
        } else {
            params = new Object[0];
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param == null) {
                    statement.setObject(i + 1, null);
                } else if (param instanceof Integer) {
                    statement.setInt(i + 1, (Integer) param);
                } else if (param instanceof Long) {
                    statement.setLong(i + 1, (Long) param);
                } else if (param instanceof Double) {
                    statement.setDouble(i + 1, (Double) param);
                } else if (param instanceof Float) {
                    statement.setFloat(i + 1, (Float) param);
                } else if (param instanceof Boolean) {
                    statement.setBoolean(i + 1, (Boolean) param);
                } else if (param instanceof String) {
                    statement.setString(i + 1, (String) param);
                } else if (param instanceof java.sql.Date) {
                    statement.setDate(i + 1, (java.sql.Date) param);
                } else if (param instanceof java.sql.Timestamp) {
                    statement.setTimestamp(i + 1, (java.sql.Timestamp) param);
                } else if (param instanceof Byte[] || param instanceof byte[]) {
                    // Support for Byte[] and byte[] for BLOB
                    if (param instanceof Byte[])
                        statement.setBytes(i + 1, toPrimitiveByteArray((Byte[]) param));
                    else // Already a primitive byte[]
                        statement.setBytes(i + 1, (byte[]) param);
                } else {
                    System.err.println("Setting parameter of type " + param.getClass().getName() + " with value: " + param.toString() + " is not supported. Using setObject instead.");
                    statement.setObject(i + 1, param);
                }
            }

            boolean isSelect = sql.trim().toLowerCase().startsWith("select");

            if (isSelect) {
                try (ResultSet rs = statement.executeQuery()) {
                    if (Iterable.class.isAssignableFrom(returnType) || returnType.isArray()) {
                        // Build a List of the desired elements, then convert to array if needed
                        List<Object> results = new ArrayList<>();
                        while (rs.next()) {
                            results.add(readResultSetValue(rs, 1, returnType.getComponentType() != null ? returnType.getComponentType() : Object.class));
                        }
                        if (returnType.isArray()) {
                            // Convert to correct array type
                            Object arr = java.lang.reflect.Array.newInstance(returnType.getComponentType(), results.size());
                            for (int i = 0; i < results.size(); i++)
                                java.lang.reflect.Array.set(arr, i, results.get(i));
                            return arr;
                        } else {
                            return results;
                        }
                    } else if (Map.class.isAssignableFrom(returnType)) {
                        // Return a Map<String, Object> (column name -> value) for the first row
                        if (rs.next()) {
                            Map<String, Object> resultMap = new LinkedHashMap<>();
                            ResultSetMetaData meta = rs.getMetaData();
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                resultMap.put(meta.getColumnName(i), rs.getObject(i));
                            }
                            return resultMap;
                        }
                        return null;
                    } else {
                        // Single result - support for primitives and entities
                        if (rs.next()) {
                            return readResultSetValue(rs, 1, returnType);
                        } else {
                            return null;
                        }
                    }
                }
            } else {
                int updated = statement.executeUpdate();
                if (returnType == int.class || returnType == Integer.class) {
                    return updated;
                }
                return null;
            }
        }
    }

    // Utility method to unbox Byte[] to byte[]
    private byte[] toPrimitiveByteArray(Byte[] bytes) {
        if (bytes == null) return null;
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[i];
        }
        return result;
    }

    // Utility for extracting and converting a ResultSet cell to the required Java type
    private Object readResultSetValue(ResultSet rs, int col, Class<?> t) throws Exception {
        if (t == null || t == Object.class) {
            return rs.getObject(col);
        }
        if (t == String.class)   return rs.getString(col);
        if (t == Integer.class || t == int.class) return rs.getInt(col);
        if (t == Long.class || t == long.class)   return rs.getLong(col);
        if (t == Double.class || t == double.class) return rs.getDouble(col);
        if (t == Float.class || t == float.class) return rs.getFloat(col);
        if (t == Boolean.class || t == boolean.class) return rs.getBoolean(col);
        if (t == Byte.class || t == byte.class)   return rs.getByte(col);
        if (t == Byte[].class) {
            byte[] primitive = rs.getBytes(col);
            if (primitive == null) return null;
            Byte[] wrapper = new Byte[primitive.length];
            for (int i = 0; i < primitive.length; i++) wrapper[i] = primitive[i];
            return wrapper;
        }
        if (t == byte[].class) return rs.getBytes(col);
        if (t == java.sql.Date.class) return rs.getDate(col);
        if (t == java.sql.Timestamp.class) return rs.getTimestamp(col);
        // Attempt to read as object, can expand to custom entity mapping if needed
        return rs.getObject(col, t);
    }

    /**
     * Handles 'findByXxxAndYyy' dynamic methods.
     */
    private Object handleFindByMethod(Method method, Object[] args, long startTime) throws Exception {
        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();

        // Check if method should return a single entity or multiple entities
        boolean isIterable = Iterable.class.isAssignableFrom(returnType) ||
                Collection.class.isAssignableFrom(returnType) ||
                returnType.isArray();

        // Extract field names from the method name
        String[] fieldNames = methodName.substring(6) // Remove "findBy"
                .split("And"); // Split by "And" for multiple fields

        // Convert first character of each field name to lowercase
        fieldNames = java.util.Arrays.stream(fieldNames)
                .map(f -> Character.toLowerCase(f.charAt(0)) + f.substring(1))
                .toArray(String[]::new);

        // Ensure the method arguments match the number of fields
        if (fieldNames.length != args.length) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        // Prepare WHERE clause with the field names and arguments
        StringBuilder whereClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            String columnName = entityMetadata.getColumnName(fieldName);

            if (columnName == null) {
                throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + entityClass.getName());
            }

            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(schemaFormatter.formatColumnName(columnName)).append(" = ?");
            parameters.add(fetchValue(args[i])); // Fetch value (handle foreign keys if needed)
        }

        // Build SQL query
        String sql = "SELECT * FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + whereClause;

        // Execute query and retrieve results
        //System.out.println("Executing query: " + sql); // Debugging log
        Object result;
        if (isIterable) {
            List<T> results = new ArrayList<>();
            database.connect(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    setStatementParameters(statement, parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            T entity = (T) mapEntity(connection, entityClass, rs);
                            results.add(entity);
                        }
                    }
                }
                return null;
            });
            result = results;
        } else {
            result = database.connect(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    setStatementParameters(statement, parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return mapEntity(connection, entityClass, rs);
                        }
                    }
                }
                return null;
            });
        }

        System.out.println("Query '" + methodName + "' took: " + (System.currentTimeMillis() - startTime) + "ms");
        return result;
    }

    /**
     * Handles 'findAllByXxxAndYyy' dynamic methods.
     */
    private Object handleFindAllByMethod(Method method, Object[] args, long startTime) throws Exception {
        String methodName = method.getName();

        if (!methodName.startsWith("findAllBy")) {
            throw new IllegalArgumentException("Invalid method name for findAllBy: " + methodName);
        }

        // Extract field names from findAllBy method
        String[] fieldNames = methodName.substring(9) // Remove "findAllBy"
                .split("And"); // Split by "And" for multiple fields

        // Convert first character of each field name to lowercase
        fieldNames = java.util.Arrays.stream(fieldNames)
                .map(f -> Character.toLowerCase(f.charAt(0)) + f.substring(1))
                .toArray(String[]::new);

        // Ensure the method arguments match the number of fields
        if (fieldNames.length != args.length) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        // Prepare WHERE clause with the field names and arguments
        StringBuilder whereClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            String columnName = entityMetadata.getColumnName(fieldName);

            if (columnName == null) {
                throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + entityClass.getName());
            }

            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(schemaFormatter.formatColumnName(columnName)).append(" = ?");
            parameters.add(fetchValue(args[i])); // Fetch value (handle foreign keys if needed)
        }

        // Build SQL query
        String sql = "SELECT * FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + whereClause;

        // Execute query and retrieve results
        //System.out.println("Executing query: " + sql); // Debugging log

        //System.out.println("Query '" + methodName + "' took: " + (System.currentTimeMillis() - startTime) + "ms");
        return database.connect(connection -> {
            List<T> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setStatementParameters(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        T entity = (T) mapEntity(connection, entityClass, rs);
                        found.add(entity);
                    }
                }
            }
            return found;
        }); // Return the list of results
    }

    /**
     * Handles 'deleteByXxxAndYyy' dynamic methods.
     */
    private Object handleDeleteByMethod(Method method, Object[] args, long startTime) throws Exception {
        String methodName = method.getName();

        if (!methodName.startsWith("deleteBy")) {
            throw new IllegalArgumentException("Invalid method name for deleteBy: " + methodName);
        }

        // Extract field names from deleteBy method
        String[] fieldNames = methodName.substring(8) // Remove "deleteBy"
                .split("And"); // Split by "And" for multiple fields

        // Convert first character of each field name to lowercase
        fieldNames = java.util.Arrays.stream(fieldNames)
                .map(f -> Character.toLowerCase(f.charAt(0)) + f.substring(1))
                .toArray(String[]::new);

        // Ensure the method arguments match the number of fields
        if (fieldNames.length != args.length) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        // Prepare WHERE clause with the field names and arguments
        StringBuilder whereClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            String columnName = entityMetadata.getColumnName(fieldName);

            if (columnName == null) {
                throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + entityClass.getName());
            }

            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(schemaFormatter.formatColumnName(columnName)).append(" = ?");
            parameters.add(fetchValue(args[i])); // Fetch value (handle foreign keys if needed)
        }

        // Build SQL query
        String sql = "DELETE FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + whereClause;

        // Execute query
        //System.out.println("Executing query: " + sql); // Debugging log
        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setStatementParameters(statement, parameters);
                statement.executeUpdate();
            }
            return null;
        });

        //System.out.println("Query '" + methodName + "' took: " + (System.currentTimeMillis() - startTime) + "ms");
        return null; // Return void
    }

    /**
     * Handles 'deleteAllByXxxAndYyy' dynamic methods.
     */
    private Object handleDeleteAllByMethod(Method method, Object[] args, long startTime) throws Exception {
        String methodName = method.getName();

        if (!methodName.startsWith("deleteAllBy")) {
            throw new IllegalArgumentException("Invalid method name for deleteAllBy: " + methodName);
        }

        // Extract field names from deleteAllBy method
        String[] fieldNames = methodName.substring(11) // Remove "deleteAllBy"
                .split("And"); // Split by "And" for multiple fields

        // Convert first character of each field name to lowercase
        fieldNames = java.util.Arrays.stream(fieldNames)
                .map(f -> Character.toLowerCase(f.charAt(0)) + f.substring(1))
                .toArray(String[]::new);

        // Ensure the method arguments match the number of fields
        if (fieldNames.length != args.length) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        // Prepare WHERE clause with the field names and arguments
        StringBuilder whereClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            String columnName = entityMetadata.getColumnName(fieldName);

            if (columnName == null) {
                throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + entityClass.getName());
            }

            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(schemaFormatter.formatColumnName(columnName)).append(" = ?");
            parameters.add(fetchValue(args[i])); // Fetch value (handle foreign keys if needed)
        }

        // Build SQL query
        String sql = "DELETE FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + whereClause;

        // Execute query
        //System.out.println("Executing query: " + sql); // Debugging log
        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setStatementParameters(statement, parameters);
                statement.executeUpdate();
            }
            return null;
        });

        //System.out.println("Query '" + methodName + "' took: " + (System.currentTimeMillis() - startTime) + "ms");
        return null; // Return void
    }

    /**
     * Handles basic Object methods like equals, hashCode, and toString.
     */
    private Object handleObjectMethods(Object proxy, Method method, Object[] args) {
        String methodName = method.getName();
        return switch (methodName) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getName() + "@" + Integer.toHexString(proxy.hashCode());
            default -> throw new UnsupportedOperationException("Method not implemented: " + methodName);
        };
    }

    // -------------------- CRUD Methods Implementation --------------------

    /**
     * Saves a single entity. If the entity already exists (based on primary key), it updates it; otherwise, it inserts it.
     */
    private <S extends T> S save(S entity) throws Exception {
        Field pkField = entityMetadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);

        if (pkValue == null || !existsByIdInternal(pkValue)) {
            insert(entity);
        } else {
            update(entity);
        }
        return entity;
    }

    /**
     * Saves multiple entities.
     */
    private <S extends T> Iterable<S> saveAll(Iterable<S> entities) throws Exception {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            save(entity);
            result.add(entity);
        }
        return result;
    }

    /**
     * Finds an entity by its ID.
     */
    private T findById(ID id) throws Exception {
        String sql = buildSelectSQL(entityMetadata.getPrimaryKeyField().getName(), false);
        return (T) database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return mapEntity(connection, entityClass, resultSet);
                    }
                }
            }
            return null;
        });
    }

    /**
     * Checks if an entity exists by its ID.
     */
    private boolean existsById(ID id) throws Exception {
        return existsByIdInternal(id);
    }

    /**
     * Internal method to check existence by ID.
     */
    private boolean existsByIdInternal(Object id) throws Exception {
        String sql = "SELECT 1 FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) + " WHERE " +
                schemaFormatter.formatColumnName(entityMetadata.getPrimaryKeyColumn()) + " = ?";
        return database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /**
     * Retrieves all entities.
     */
    private @NotNull Iterable<T> findAll() throws Exception {
        return database.connect(connection -> {
            String sql = "SELECT * FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName());
            List<T> results = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    T entity = (T) mapEntity(connection, entityClass, rs);
                    results.add(entity);
                }
            }
            return results;
        });
    }

    /**
     * Retrieves all entities by their IDs.
     */
    private Iterable<T> findAllById(Iterable<ID> ids) throws Exception {
        List<ID> idList = new ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = idList.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT * FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + schemaFormatter.formatColumnName(entityMetadata.getPrimaryKeyColumn()) + " IN (" + placeholders + ")";

        return database.connect(connection -> {
            List<T> results = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (ID id : idList) {
                    statement.setObject(index++, id);
                }
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        T entity = (T) mapEntity(connection, entityClass, rs);
                        results.add(entity);
                    }
                }
            }
            return results;
        });
    }

    /**
     * Counts the total number of entities.
     */
    private long count() throws Exception {
        String sql = "SELECT COUNT(*) FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName());
        return database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0L;
        });
    }

    /**
     * Deletes an entity by its ID.
     */
    private void deleteById(ID id) throws Exception {
        String sql = "DELETE FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + schemaFormatter.formatColumnName(entityMetadata.getPrimaryKeyColumn()) + " = ?";
        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Deletes a given entity.
     */
    private void delete(T entity) throws Exception {
        Field pkField = entityMetadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);
        if (pkValue == null) {
            throw new IllegalArgumentException("Entity primary key cannot be null for deletion.");
        }
        deleteById((ID) pkValue);
    }

    /**
     * Deletes multiple entities by their IDs.
     */
    private void deleteAllById(Iterable<? extends ID> ids) throws Exception {
        List<ID> idList = new ArrayList<>();
        ids.forEach(idList::add);
        if (idList.isEmpty()) {
            return;
        }

        String placeholders = idList.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "DELETE FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName()) +
                " WHERE " + schemaFormatter.formatColumnName(entityMetadata.getPrimaryKeyColumn()) + " IN (" + placeholders + ")";

        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                for (ID id : idList) {
                    statement.setObject(index++, id);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Deletes all given entities.
     */
    private void deleteAll(Iterable<? extends T> entities) throws Exception {
        for (T entity : entities) {
            delete(entity);
        }
    }

    /**
     * Deletes all entities in the repository.
     */
    private void deleteAll() throws Exception {
        String sql = "DELETE FROM " + schemaFormatter.formatTableName(entityMetadata.getTableName());
        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
            return null;
        });
    }

    // -------------------- Helper Methods for CRUD Operations --------------------

    /**
     * Inserts a new entity into the database.
     */
    private void insert(T entity) throws Exception {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();

        for (Map.Entry<String, String> entry : entityMetadata.fieldToColumnMap.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            Field field = entityMetadata.getField(fieldName);

            if (field.getName().equals("modifiedFields")) continue;

            Object value = field.get(entity);

            // Skip primary key if it's auto-generated
            if (field.equals(entityMetadata.getPrimaryKeyField()) && isAutoGenerated(field)) {
                continue;
            }

            if (field.isAnnotationPresent(Temporal.class)) {
                value = new Timestamp((long)value);
            }

            columns.add(schemaFormatter.formatColumnName(columnName));
            values.add(value);
            placeholders.add("?");
        }

        String sql = "INSERT INTO " + schemaFormatter.formatTableName(entityMetadata.getTableName()) + " (" +
                String.join(", ", columns) + ") VALUES (" +
                String.join(", ", placeholders) + ")";

        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                setStatementParameters(statement, values);
                statement.executeUpdate();

                // If primary key is auto-generated, set it back to the entity
                if (isAutoGenerated(entityMetadata.getPrimaryKeyField())) {
                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            Object generatedId = generatedKeys.getObject(1);
                            if (generatedId instanceof BigInteger && entityMetadata.getPrimaryKeyField().getType() == Long.class) {
                                generatedId = ((BigInteger) generatedId).longValue();
                            }
                            entityMetadata.getPrimaryKeyField().set(entity, generatedId);
                        }
                    }
                }
            }
            return null;
        });
    }

    /**
     * Updates an existing entity in the database.
     */
    private void update(T entity) throws Exception {
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, String> entry : entityMetadata.fieldToColumnMap.entrySet()) {
            String fieldName = entry.getKey();

            //Check if field is modified
            try {
                Method isFieldModified = entity.getClass().getDeclaredMethod("isFieldModified", String.class);
                if (!(boolean) isFieldModified.invoke(entity, fieldName)) {
                    continue; // Skip unchanged fields
                }
            } catch (NoSuchMethodException ignored) {
                //No field found, we continue as usual
            }

            String columnName = entry.getValue();

            // Skip primary key in SET clause
            if (fieldName.equals(entityMetadata.getPrimaryKeyField().getName())) {
                continue;
            }

            Field field = entityMetadata.getField(fieldName);
            Object value = field.get(entity);

            //Check if the field is annotated with @Temporal
            Temporal temporal = field.getAnnotation(Temporal.class);
            if (temporal != null) {
                value = new Timestamp((long)value);
            }

            setClauses.add(schemaFormatter.formatColumnName(columnName) + " = ?");
            values.add(value);
        }

        if (values.isEmpty()) {
            return;
        }

        //Reset modified fields
        try {
            Method resetModifiedFields = entity.getClass().getDeclaredMethod("resetModifiedFields");
            resetModifiedFields.setAccessible(true);
            resetModifiedFields.invoke(entity);
        } catch (Exception ignored) {}

        String sql = "UPDATE " + schemaFormatter.formatTableName(entityMetadata.getTableName()) + " SET " +
                String.join(", ", setClauses) + " WHERE " +
                schemaFormatter.formatColumnName(entityMetadata.getPrimaryKeyColumn()) + " = ?";

        // Add primary key value at the end
        Field pkField = entityMetadata.getPrimaryKeyField();
        Object pkValue = pkField.get(entity);
        values.add(pkValue);

        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setStatementParameters(statement, values);
                statement.executeUpdate();
            } catch (Exception e) {
                System.err.println("Error executing update statement: " + sql);
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Sets parameters for PreparedStatement.
     */
    private void setStatementParameters(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            switch (value) {
                case Timestamp timestamp -> statement.setTimestamp(i + 1, timestamp);
                case Date date -> statement.setDate(i + 1, (java.sql.Date) value);
                case Enum<?> anEnum -> statement.setString(i + 1, anEnum.name());
                case null, default -> statement.setObject(i + 1, value);
            }
        }
    }

    /**
     * Checks if a field is auto-generated (e.g., auto-incremented primary key).
     * This method can be customized based on your annotation or naming conventions.
     */
    private boolean isAutoGenerated(Field field) {
        // Example: Check for @GeneratedValue annotation
        return field.isAnnotationPresent(Column.class) && field.getAnnotation(Column.class).autoIncrement();
    }

    // -------------------- Handle FindBy Methods --------------------

    /**
     * Fetches the value of the field, or the primary key if it's an entity.
     * @param value The value to fetch.
     * @return The fetched value.
     */
    private Object fetchValue(Object value) {
        if (value.getClass().isAnnotationPresent(Entity.class)) {
            //We must use it's primary key
            EntityMetadata metadata = new EntityMetadata(value.getClass());
            Field pkField = metadata.getPrimaryKeyField();
            try {
                value = pkField.get(value);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    /**
     * Executes a query that expects a single result.
     */
    private Object executeFindSingle(String fieldName, Object value) throws Exception {
        String sql = buildSelectSQL(fieldName, false);
        return database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return mapEntity(connection, entityClass, resultSet);
                    }
                }
            }
            return null;
        });
    }

    /**
     * Executes a query that expects multiple results.
     */
    private Object executeFindMultiple(String fieldName, Object value) throws Exception {
        String sql = buildSelectSQL(fieldName, true);
        List<T> results = new ArrayList<>();
        database.connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, value);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        T entity = (T) mapEntity(connection, entityClass, resultSet);
                        results.add(entity);
                    }
                }
            }
            return null;
        });
        return results;
    }

    /**
     * Builds the SELECT SQL statement based on the field and expected result type.
     *
     * @param fieldName  The field to filter by.
     * @param isMultiple Whether the query expects multiple results.
     * @return The SQL query string.
     */
    private String buildSelectSQL(String fieldName, boolean isMultiple) {
        // Utilize cached query if available
        String cacheKey = "findBy_" + fieldName + (isMultiple ? "_multiple" : "_single");
        return queryCache.computeIfAbsent(cacheKey, key -> {
            String tableName = entityMetadata.getTableName();
            String columnName = entityMetadata.getColumnName(fieldName);
            if (columnName == null) {
                throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + entityClass.getName());
            }
            return "SELECT * FROM " + schemaFormatter.formatTableName(tableName) + " WHERE " + schemaFormatter.formatColumnName(columnName) + " = ?";
        });
    }

    // -------------------- Entity Mapping --------------------

    /**
     * Maps a ResultSet to an entity instance, utilizing cached metadata.
     *
     * @param connection The database connection.
     * @param entityCls  The class of the entity.
     * @param resultSet  The ResultSet from the query.
     * @return The mapped entity instance.
     * @throws Exception If an error occurs during mapping.
     */
    private Object mapEntity(Connection connection, Class<?> entityCls, ResultSet resultSet) throws Exception {
        EntityMetadata metadata = new EntityMetadata(entityCls);
        Object entityInstance = dependencyContainer.newInstance(entityCls);

        for (Map.Entry<String, String> entry : metadata.fieldToColumnMap.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            Field field = metadata.getField(fieldName);

            if (field == null || field.getName().equals("modifiedFields")) {
                continue; // Skip if field not found
            }

            // Check if field is a foreign key (i.e., another entity)
            if (field.getType().isAnnotationPresent(Entity.class)) {
                Object foreignKeyValue = resultSet.getObject(columnName);
                if (foreignKeyValue != null) {
                    Object foreignEntity = fetchForeignEntity(connection, field.getType(), metadata.getPrimaryKeyColumn(), foreignKeyValue);
                    field.set(entityInstance, foreignEntity);
                }
            } else {
                Object value = resultSet.getObject(columnName);

                // Handle UUID conversion
                if (field.getType() == UUID.class && value instanceof String) {
                    value = UUID.fromString((String) value);
                }

                // Handle Temporal types
                Temporal temporal = field.getAnnotation(Temporal.class);
                if (temporal != null) {
                    switch (temporal.value()) {
                        case DATE:
                            value = resultSet.getDate(columnName);
                            break;
                        case TIME:
                            value = resultSet.getTime(columnName);
                            break;
                        case TIMESTAMP:
                            Timestamp ts = resultSet.getTimestamp(columnName);
                            value = ts != null ? ts.getTime() : null;
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported Temporal type");
                    }
                }

                //Check if field is an enum
                if (field.getType().isEnum()) {
                    value = Enum.valueOf((Class<? extends Enum>) field.getType(), value.toString());
                }

                // Handle blob conversion
                if (field.getType() == byte[].class && value instanceof Blob) {
                    Blob blob = (Blob) value;
                    if (blob.length() == 0) {
                        field.set(entityInstance, new byte[0]);
                    } else {
                        field.set(entityInstance, blob.getBytes(1, (int) blob.length()));
                    }
                    continue;
                }


                field.set(entityInstance, value);
            }
        }

        return entityInstance;
    }

    /**
     * Fetches a foreign entity based on the foreign key value.
     *
     * @param connection        The database connection.
     * @param foreignEntityCls  The class of the foreign entity.
     * @param foreignKeyColumn  The foreign key column name.
     * @param foreignKeyValue   The value of the foreign key.
     * @return The fetched foreign entity instance.
     * @throws Exception If an error occurs during fetching.
     */
    private Object fetchForeignEntity(Connection connection, Class<?> foreignEntityCls,
                                      String foreignKeyColumn, Object foreignKeyValue) throws Exception {
        EntityMetadata foreignMetadata = new EntityMetadata(foreignEntityCls);
        String tableName = foreignMetadata.getTableName();
        String pkColumn = foreignMetadata.getPrimaryKeyColumn();

        String sql = "SELECT * FROM " + schemaFormatter.formatTableName(tableName) + " WHERE " + schemaFormatter.formatColumnName(pkColumn) + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, foreignKeyValue);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapEntity(connection, foreignEntityCls, rs);
                } else {
                    throw new IllegalArgumentException("Foreign entity not found: " + foreignEntityCls.getName() +
                            " WHERE " + pkColumn + " = " + foreignKeyValue);
                }
            }
        }
    }

    // -------------------- Nested Classes --------------------

    /**
     * Represents metadata of an entity to avoid repeated reflection.
     */
    private static class EntityMetadata {
        private final String tableName;
        private final Map<String, String> fieldToColumnMap = new HashMap<>();
        private final Map<String, Field> fieldsMap = new HashMap<>();
        private final String primaryKeyColumn;
        private final Field primaryKeyField;

        public EntityMetadata(Class<?> entityClass) {
            Entity entity = entityClass.getAnnotation(Entity.class);
            if (entity == null) {
                throw new IllegalArgumentException("Entity annotation not found on class: " + entityClass.getName());
            }
            this.tableName = Database.getTablePrefix()
                    + (entity.table().isEmpty() ? entityClass.getSimpleName().toLowerCase() : entity.table());

            String pkColumn = null;
            Field pkField = null;

            for (Field field : entityClass.getDeclaredFields()) {
                field.setAccessible(true);
                String columnName = field.getName();
                if (field.isAnnotationPresent(Column.class)) {
                    Column column = field.getAnnotation(Column.class);
                    if (!column.name().isEmpty()) {
                        columnName = column.name();
                    }
                    if (column.primaryKey()) {
                        pkColumn = columnName;
                        pkField = field;
                    }
                } else if (field.isAnnotationPresent(Temporal.class)) {
                    Temporal temporal = field.getAnnotation(Temporal.class);
                    if (!temporal.name().isEmpty()) {
                        columnName = temporal.name();
                    }
                }
                fieldToColumnMap.put(field.getName(), columnName);
                fieldsMap.put(field.getName(), field);
            }

            if (pkColumn == null) {
                throw new IllegalArgumentException("No primary key defined in entity: " + entityClass.getName());
            }
            this.primaryKeyColumn = pkColumn;
            this.primaryKeyField = pkField;
        }

        public String getTableName() {
            return tableName;
        }

        public String getColumnName(String fieldName) {
            return fieldToColumnMap.get(fieldName);
        }

        public Field getField(String fieldName) {
            return fieldsMap.get(fieldName);
        }

        public String getPrimaryKeyColumn() {
            return primaryKeyColumn;
        }

        public Field getPrimaryKeyField() {
            return primaryKeyField;
        }

        public Map<String, String> getFieldToColumnMap() {
            return fieldToColumnMap;
        }
    }
}