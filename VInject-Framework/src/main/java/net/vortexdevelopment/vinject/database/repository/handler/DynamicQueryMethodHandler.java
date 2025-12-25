package net.vortexdevelopment.vinject.database.repository.handler;

import net.vortexdevelopment.vinject.database.repository.EntityMetadata;
import net.vortexdevelopment.vinject.database.repository.RepositoryInvocationContext;
import net.vortexdevelopment.vinject.database.repository.RepositoryUtils;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Handles dynamic findBy, findAllBy, deleteBy, and deleteAllBy methods.
 */
public class DynamicQueryMethodHandler extends BaseMethodHandler {

    @Override
    public boolean canHandle(Method method) {
        String name = method.getName();
        return name.startsWith("findBy") || name.startsWith("findAllBy") ||
               name.startsWith("deleteBy") || name.startsWith("deleteAllBy");
    }

    @Override
    public Object handle(RepositoryInvocationContext<?, ?> context, Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        long startTime = System.currentTimeMillis();

        Object result = switch (methodName) {
            case String n when n.startsWith("findBy") -> handleFindBy(context, method, args);
            case String n when n.startsWith("findAllBy") -> handleFindAllBy(context, method, args);
            case String n when n.startsWith("deleteBy") -> handleDeleteBy(context, method, args);
            case String n when n.startsWith("deleteAllBy") -> handleDeleteAllBy(context, method, args);
            default -> null;
        };

        System.out.println("Query '" + methodName + "' took: " + (System.currentTimeMillis() - startTime) + "ms");
        return result;
    }

    private Object handleFindBy(RepositoryInvocationContext<?, ?> context, Method method, Object[] args) throws Exception {
        String methodName = method.getName();
        Class<?> returnType = method.getReturnType();
        EntityMetadata metadata = context.getEntityMetadata();

        boolean isIterable = Iterable.class.isAssignableFrom(returnType) ||
                Collection.class.isAssignableFrom(returnType) ||
                returnType.isArray();

        String[] fieldNames = parseFieldNames(methodName, 6);

        if (fieldNames.length != (args == null ? 0 : args.length)) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        WhereClause where = buildWhereClause(context, fieldNames, args);
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + where.sql;

        if (isIterable) {
            return context.getDatabase().connect(connection -> {
                List<Object> results = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    RepositoryUtils.setStatementParameters(statement, where.parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            results.add(mapEntity(context, connection, context.getEntityClass(), rs));
                        }
                    }
                }
                return results;
            });
        } else {
            return context.getDatabase().connect(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    RepositoryUtils.setStatementParameters(statement, where.parameters);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            return mapEntity(context, connection, context.getEntityClass(), rs);
                        }
                    }
                }
                return null;
            });
        }
    }

    private Object handleFindAllBy(RepositoryInvocationContext<?, ?> context, Method method, Object[] args) throws Exception {
        String methodName = method.getName();
        EntityMetadata metadata = context.getEntityMetadata();

        String[] fieldNames = parseFieldNames(methodName, 9);

        if (fieldNames.length != (args == null ? 0 : args.length)) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        WhereClause where = buildWhereClause(context, fieldNames, args);
        String sql = "SELECT * FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + where.sql;

        return context.getDatabase().connect(connection -> {
            List<Object> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                RepositoryUtils.setStatementParameters(statement, where.parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        found.add(mapEntity(context, connection, context.getEntityClass(), rs));
                    }
                }
            }
            return found;
        });
    }

    private Object handleDeleteBy(RepositoryInvocationContext<?, ?> context, Method method, Object[] args) throws Exception {
        String methodName = method.getName();
        EntityMetadata metadata = context.getEntityMetadata();

        String[] fieldNames = parseFieldNames(methodName, 8);

        if (fieldNames.length != (args == null ? 0 : args.length)) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        WhereClause where = buildWhereClause(context, fieldNames, args);
        String sql = "DELETE FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + where.sql;

        context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                RepositoryUtils.setStatementParameters(statement, where.parameters);
                statement.executeUpdate();
            }
            return null;
        });
        return null;
    }

    private Object handleDeleteAllBy(RepositoryInvocationContext<?, ?> context, Method method, Object[] args) throws Exception {
        String methodName = method.getName();
        EntityMetadata metadata = context.getEntityMetadata();

        String[] fieldNames = parseFieldNames(methodName, 11);

        if (fieldNames.length != (args == null ? 0 : args.length)) {
            throw new IllegalArgumentException("Mismatch between fields and arguments in method: " + methodName);
        }

        WhereClause where = buildWhereClause(context, fieldNames, args);
        String sql = "DELETE FROM " + context.getSchemaFormatter().formatTableName(metadata.getTableName()) +
                " WHERE " + where.sql;

        context.getDatabase().connect(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                RepositoryUtils.setStatementParameters(statement, where.parameters);
                statement.executeUpdate();
            }
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

    private WhereClause buildWhereClause(RepositoryInvocationContext<?, ?> context, String[] fieldNames, Object[] args) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        EntityMetadata metadata = context.getEntityMetadata();

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
            parameters.add(RepositoryUtils.unwrapEntityId(args[i], context));
        }
        return new WhereClause(sql.toString(), parameters);
    }

    private record WhereClause(String sql, List<Object> parameters) {}
}
