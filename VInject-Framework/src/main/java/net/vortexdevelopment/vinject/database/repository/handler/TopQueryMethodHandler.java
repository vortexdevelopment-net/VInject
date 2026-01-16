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

/**
 * Handles dynamic findTopN and findFirstN methods.
 */
public class TopQueryMethodHandler extends BaseMethodHandler {

    @Override
    public boolean canHandle(Method method) {
        String name = method.getName();
        return name.startsWith("findTop") || name.startsWith("findFirst");
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();
        EntityMetadata metadata = context.getEntityMetadata();

        boolean isIterable = Iterable.class.isAssignableFrom(returnType) ||
                Collection.class.isAssignableFrom(returnType) ||
                returnType.isArray();

        int limit = 1;
        String remaining;

        if (methodName.startsWith("findTop")) {
            remaining = methodName.substring(7);
        } else if (methodName.startsWith("findFirst")) {
            remaining = methodName.substring(9);
        } else {
            return null;
        }

        int byIndex = remaining.indexOf("By");
        if (byIndex > 0) {
            try {
                limit = Integer.parseInt(remaining.substring(0, byIndex));
                remaining = remaining.substring(byIndex);
            } catch (NumberFormatException e) {
                limit = 1;
            }
        } else {
            limit = 1;
        }

        String orderByField = null;
        String orderDirection = "ASC";

        int orderByIndex = remaining.indexOf("OrderBy");
        if (orderByIndex == -1) {
            throw new IllegalArgumentException("Method name must contain 'OrderBy': " + methodName);
        }

        String wherePart = remaining.substring(0, orderByIndex);
        String orderByPart = remaining.substring(orderByIndex + 8);

        if (orderByPart.endsWith("Desc")) {
            orderByField = orderByPart.substring(0, orderByPart.length() - 4);
            orderDirection = "DESC";
        } else if (orderByPart.endsWith("Asc")) {
            orderByField = orderByPart.substring(0, orderByPart.length() - 3);
            orderDirection = "ASC";
        } else {
            orderByField = orderByPart;
            orderDirection = "ASC";
        }

        orderByField = Character.toLowerCase(orderByField.charAt(0)) + orderByField.substring(1);
        String orderByColumn = metadata.getColumnName(orderByField);
        if (orderByColumn == null) {
            throw new IllegalArgumentException("No such field: " + orderByField + " in entity " + context.getEntityClass().getName());
        }

        StringBuilder whereClause = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        if (wherePart.length() > 2) { 
            String conditionsPart = wherePart.substring(2); 
            String[] fieldNames = conditionsPart.split("And");

            fieldNames = java.util.Arrays.stream(fieldNames)
                    .map(f -> Character.toLowerCase(f.charAt(0)) + f.substring(1))
                    .toArray(String[]::new);

            if (fieldNames.length != (args == null ? 0 : args.length)) {
                throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
            }

            for (int i = 0; i < fieldNames.length; i++) {
                String fieldName = fieldNames[i];
                String columnName = metadata.getColumnName(fieldName);

                if (columnName == null) {
                    throw new IllegalArgumentException("No such field: " + fieldName + " in entity " + context.getEntityClass().getName());
                }

                if (i > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append(context.getSchemaFormatter().formatColumnName(columnName)).append(" = ?");
                parameters.add(RepositoryUtils.unwrapEntityId(args[i], context));
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(context.getSchemaFormatter().formatTableName(metadata.getTableName()));

        if (whereClause.length() > 0) {
            sql.append(" WHERE ").append(whereClause);
        }

        sql.append(" ORDER BY ").append(context.getSchemaFormatter().formatColumnName(orderByColumn)).append(" ").append(orderDirection);
        sql.append(" LIMIT ").append(limit);

        long start = System.nanoTime();
        Object result;
        if (isIterable) {
            result = context.getDatabase().connect(connection -> {
                List<Object> results = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                    RepositoryUtils.setStatementParameters(statement, parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            results.add(mapEntity(context, connection, context.getEntityClass(), rs));
                        }
                    }
                }
                return results;
            });
        } else {
            result = context.getDatabase().connect(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                    RepositoryUtils.setStatementParameters(statement, parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return mapEntity(context, connection, context.getEntityClass(), rs);
                        }
                    }
                }
                return null;
            });
        }
        long end = System.nanoTime();
        long totalNano = end - start;

        DebugLogger.log(context.getRepositoryClass(), "TOP QUERY '%s' executed. Total Time: %d ns (%.3f ms)",
                methodName, totalNano, totalNano / 1_000_000.0);
        return result;
    }
}
