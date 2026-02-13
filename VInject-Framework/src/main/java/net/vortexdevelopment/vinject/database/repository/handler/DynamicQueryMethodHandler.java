package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.database.repository.EntityMetadata;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryUtils;
import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles dynamic findBy, findAllBy, deleteBy, and deleteAllBy methods.
 */
public class DynamicQueryMethodHandler extends BaseMethodHandler {

    private final Map<Method, QueryInfo> queryCache = new ConcurrentHashMap<>();

    @Override
    public boolean canHandle(Method method) {
        String name = method.getName();
        return name.startsWith("findBy") || name.startsWith("findAllBy") ||
               name.startsWith("deleteBy") || name.startsWith("deleteAllBy");
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        long start = System.nanoTime();

        QueryInfo info = queryCache.computeIfAbsent(method, m -> buildQueryInfo(context, m));

        long[] internalTimings = new long[2]; // [0] = connTime, [1] = execTime

        Object result;

        if (methodName.startsWith("findBy") || methodName.startsWith("findAllBy")) {
            result = handleFind(context, method, args, info, internalTimings);
        } else if (methodName.startsWith("deleteBy") || methodName.startsWith("deleteAllBy")) {
            result = handleDelete(context, method, args, info, internalTimings);
        } else {
            result = null;
        }

        long end = System.nanoTime();
        long totalNano = end - start;

        DebugLogger.log(context.getRepositoryClass(), "DYNAMIC QUERY '%s' executed. Total Time: %d ns (%.3f ms)",
                methodName, totalNano, totalNano / 1_000_000.0);
        return result;
    }

    private QueryInfo buildQueryInfo(RepositoryInvocationContext<?, ?> context, Method method) {
        String methodName = method.getName();
        EntityMetadata metadata = context.getEntityMetadata();
        
        int prefixLength;
        boolean isDelete = false;
        boolean isMultiple = false;

        if (methodName.startsWith("findBy")) {
            prefixLength = 6;
        } else if (methodName.startsWith("findAllBy")) {
            prefixLength = 9;
            isMultiple = true;
        } else if (methodName.startsWith("deleteBy")) {
            prefixLength = 8;
            isDelete = true;
        } else if (methodName.startsWith("deleteAllBy")) {
            prefixLength = 11;
            isDelete = true;
            isMultiple = true;
        } else {
            throw new IllegalArgumentException("Unsupported method: " + methodName);
        }

        String[] fieldNames = parseFieldNames(methodName, prefixLength);
        StringBuilder sql = new StringBuilder();
        
        if (isDelete) {
            sql.append("DELETE FROM ");
        } else {
            sql.append("SELECT * FROM ");
        }
        
        sql.append(context.getSchemaFormatter().formatTableName(metadata.getTableName()));
        sql.append(" WHERE ");

        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            String columnName = metadata.getColumnName(fieldName);
            if (columnName == null) {
                throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + context.getEntityClass().getName());
            }
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(context.getSchemaFormatter().formatColumnName(columnName)).append(" = ?");
        }

        return new QueryInfo(sql.toString(), fieldNames, isDelete, isMultiple);
    }

    private Object handleFind(RepositoryInvocationContext<?, ?> context, Method method, Object[] args, QueryInfo info, long[] timings) throws Exception {
        Class<?> returnType = method.getReturnType();
        boolean isCollection = Iterable.class.isAssignableFrom(returnType) ||
                Collection.class.isAssignableFrom(returnType) ||
                returnType.isArray();

        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < info.fieldNames.length; i++) {
            parameters.add(RepositoryUtils.unwrapEntityId(args[i], context));
        }

        long connStart = System.currentTimeMillis();
        return context.getDatabase().connect(connection -> {
            timings[0] = System.currentTimeMillis() - connStart;
            long execStart = System.currentTimeMillis();
            
            Object result;
            if (isCollection || info.isMultiple) {
                List<Object> results = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(info.sql)) {
                    RepositoryUtils.setStatementParameters(statement, parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            results.add(mapEntity(context, connection, context.getEntityClass(), rs));
                        }
                    }
                }
                result = results;
            } else {
                result = null;
                try (PreparedStatement statement = connection.prepareStatement(info.sql)) {
                    RepositoryUtils.setStatementParameters(statement, parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            result = mapEntity(context, connection, context.getEntityClass(), rs);
                        }
                    }
                }
            }
            timings[1] = System.currentTimeMillis() - execStart;
            return result;
        });
    }

    private Object handleDelete(RepositoryInvocationContext<?, ?> context, Method method, Object[] args, QueryInfo info, long[] timings) throws Exception {
        List<Object> parameters = new ArrayList<>();
        for (int i = 0; i < info.fieldNames.length; i++) {
            parameters.add(RepositoryUtils.unwrapEntityId(args[i], context));
        }

        long connStart = System.currentTimeMillis();
        context.getDatabase().connect(connection -> {
            timings[0] = System.currentTimeMillis() - connStart;
            long execStart = System.currentTimeMillis();
            
            try (PreparedStatement statement = connection.prepareStatement(info.sql)) {
                RepositoryUtils.setStatementParameters(statement, parameters);
                statement.executeUpdate();
            }
            
            timings[1] = System.currentTimeMillis() - execStart;
            return null;
        });
        return null;
    }



    private String[] parseFieldNames(String methodName, int prefixLength) {
        String[] parts = methodName.substring(prefixLength).split("And");
        return java.util.Arrays.stream(parts)
                .map(f -> Character.toLowerCase(f.charAt(0)) + f.substring(1))
                .toArray(String[]::new);
    }

    private record QueryInfo(String sql, String[] fieldNames, boolean isDelete, boolean isMultiple) {}
}
