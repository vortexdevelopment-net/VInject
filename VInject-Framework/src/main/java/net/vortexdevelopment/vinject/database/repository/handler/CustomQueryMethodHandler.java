package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryUtils;
import net.vortexdevelopment.vinject.debug.DebugLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Handles the custom @Query or .query() method calls.
 */
public class CustomQueryMethodHandler extends BaseMethodHandler {

    @Override
    public boolean canHandle(Method method) {
        return method.getName().equals("query");
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException("Query string and result type must be provided!");
        }
        
        String originalSql = (String) args[0];
        Class<?> returnType = (Class<?>) args[1];
        
        String baseTableName = context.getEntityMetadata().getBaseTableName();
        String prefixedTableName = context.getEntityMetadata().getTableName();
        final String sql;
        if (!baseTableName.equals(prefixedTableName)) {
            sql = originalSql.replaceAll("(?i)\\b" + Pattern.quote(baseTableName) + "\\b", prefixedTableName);
        } else {
            sql = originalSql;
        }

        int paramCount = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') {
                paramCount++;
            }
        }

        Object[] params;
        int passedParams;
        if (args.length >= 3 && args[2] instanceof Object[]) {
            params = (Object[]) args[2];
            passedParams = params.length;
        } else if (args.length > 2) {
            params = Arrays.copyOfRange(args, 2, args.length);
            passedParams = params.length;
        } else {
            params = new Object[0];
            passedParams = 0;
        }

        if (paramCount != passedParams) {
            throw new IllegalArgumentException("Mismatch between query parameters and arguments. Expected " + paramCount + ", got " + passedParams);
        }

        long start = System.nanoTime();
        Object result = context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                RepositoryUtils.setStatementParameters(statement, Arrays.asList(params));

                boolean isSelect = sql.trim().toLowerCase().startsWith("select");

                if (isSelect) {
                    try (ResultSet rs = statement.executeQuery()) {
                        boolean isEntityType = returnType.isAnnotationPresent(Entity.class);
                        Class<?> elementType = null;

                        if (Iterable.class.isAssignableFrom(returnType) || returnType.isArray()) {
                            if (returnType.isArray()) {
                                elementType = returnType.getComponentType();
                            } else {
                                Type genericReturnType = method.getGenericReturnType();
                                if (genericReturnType instanceof java.lang.reflect.ParameterizedType pt) {
                                    Type[] typeArgs = pt.getActualTypeArguments();
                                    if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> cls) {
                                        elementType = cls;
                                    }
                                }
                            }

                            if (elementType == null) {
                                elementType = context.getEntityClass();
                            }

                            boolean isElementEntity = elementType != null && elementType.isAnnotationPresent(Entity.class);

                            if (isElementEntity) {
                                List<Object> results = new ArrayList<>();
                                while (rs.next()) {
                                    results.add(mapEntity(context, connection, elementType, rs));
                                }
                                if (returnType.isArray()) {
                                    Object arr = java.lang.reflect.Array.newInstance(elementType, results.size());
                                    for (int i = 0; i < results.size(); i++) java.lang.reflect.Array.set(arr, i, results.get(i));
                                    return arr;
                                }
                                return results;
                            }
                        }

                        if (Iterable.class.isAssignableFrom(returnType) || returnType.isArray()) {
                            List<Object> results = new ArrayList<>();
                            while (rs.next()) {
                                results.add(RepositoryUtils.readResultSetValue(rs, 1, elementType != null ? elementType : Object.class));
                            }
                            if (returnType.isArray()) {
                                Object arr = java.lang.reflect.Array.newInstance(elementType != null ? elementType : Object.class, results.size());
                                for (int i = 0; i < results.size(); i++) java.lang.reflect.Array.set(arr, i, results.get(i));
                                return arr;
                            }
                            return results;
                        } else if (Map.class.isAssignableFrom(returnType)) {
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
                            if (rs.next()) {
                                if (isEntityType) {
                                    return mapEntity(context, connection, returnType, rs);
                                } else {
                                    return RepositoryUtils.readResultSetValue(rs, 1, returnType);
                                }
                            }
                            return null;
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
        });
        long end = System.nanoTime();
        long totalNano = end - start;

        DebugLogger.log(context.getRepositoryClass(), "CUSTOM QUERY EXECUTED: %s. Time: %d ns (%.3f ms)",
                sql, totalNano, totalNano / 1_000_000.0);
        return result;
    }
}
