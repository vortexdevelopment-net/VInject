package net.vortexdevelopment.vinject.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;
import net.vortexdevelopment.vinject.database.formatter.H2SchemaFormatter;
import net.vortexdevelopment.vinject.database.formatter.MySQLSchemaFormatter;
import net.vortexdevelopment.vinject.database.formatter.SchemaFormatter;
import net.vortexdevelopment.vinject.database.mapper.H2Mapper;
import net.vortexdevelopment.vinject.database.mapper.MySQLMapper;
import net.vortexdevelopment.vinject.database.meta.EntityMetadata;
import net.vortexdevelopment.vinject.database.meta.FieldMetadata;
import net.vortexdevelopment.vinject.database.meta.ForeignKeyMetadata;
import net.vortexdevelopment.vinject.database.meta.IndexMetadata;
import net.vortexdevelopment.vinject.database.serializer.DatabaseSerializer;
import net.vortexdevelopment.vinject.database.serializer.SerializerRegistry;
import net.vortexdevelopment.vinject.debug.DebugLogger;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class Database implements DatabaseConnector {
    private static final Pattern UNIQUE_TOKEN_PATTERN = Pattern.compile("\\s+UNIQUE\\b", Pattern.CASE_INSENSITIVE);

    HikariConfig hikariConfig;
    private HikariDataSource hikariDataSource;
    private Map<Class<?>, EntityMetadata> entityMetadataMap = new HashMap<>();
    private static String TABLE_PREFIX = "example_";
    private static SQLTypeMapper sqlTypeMapper;
    @Getter private SchemaFormatter schemaFormatter;
    @Getter private final SerializerRegistry serializerRegistry = new SerializerRegistry();
    @Getter private boolean initialized = false;

    public Database() {
    }

    public Database(String host, String port, String database, String type, String username, String password, int maxPoolSize, File h2File) {
        init(host, port, database, type, username, password, maxPoolSize, h2File, true);
    }

    public Database(String host, String port, String database, String type, String username, String password, int maxPoolSize, File h2File, boolean h2ServerModeEnabled) {
        init(host, port, database, type, username, password, maxPoolSize, h2File, h2ServerModeEnabled);
    }

    /**
     * Initialize the database connection parameters.
     */
    public void init(String host, String port, String database, String type, String username, String password, int maxPoolSize, File h2File) {
        init(host, port, database, type, username, password, maxPoolSize, h2File, true);
    }

    /**
     * Initialize the database connection parameters.
     *
     * @param h2ServerModeEnabled when {@code true}, file-based H2 databases use AUTO_SERVER mode (default).
     */
    public void init(String host, String port, String database, String type, String username, String password, int maxPoolSize, File h2File, boolean h2ServerModeEnabled) {
        hikariConfig = new HikariConfig();

        if (type.equalsIgnoreCase("h2")) {
            //Set system propery vinject.database=h2
            sqlTypeMapper = new H2Mapper();
            schemaFormatter = new H2SchemaFormatter();
            System.setProperty("vinject.database", "h2");
            hikariConfig.setDriverClassName("org.h2.Driver");

            // If h2 file name is "mem" use in-memory database
            if (h2File.getName().equalsIgnoreCase("mem")) {
                hikariConfig.setJdbcUrl("jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
            } else {
                // Use AUTO_SERVER=TRUE to allow multiple connections to the same file
                // AUTO_RECONNECT=TRUE enables automatic reconnection
                // Note: DB_CLOSE_ON_EXIT cannot be used with AUTO_SERVER
                String filePath = h2File.getPath().replaceAll("\\\\", "/");
                hikariConfig.setJdbcUrl("jdbc:h2:file:./" + filePath + (h2ServerModeEnabled ? ";AUTO_SERVER=TRUE" : "") + ";AUTO_RECONNECT=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
            }

        } else {
            sqlTypeMapper = new MySQLMapper();
            schemaFormatter = new MySQLSchemaFormatter();

            switch (type.toLowerCase(Locale.ENGLISH)) {
                case "mysql" -> hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
                case "mariadb" -> hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
                default -> throw new IllegalArgumentException("Unsupported database type: " + type);
            }

            hikariConfig.setJdbcUrl("jdbc:" + type + "://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false");
        }

        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        this.initialized = true;
    }

    /**
     * Initialize the database connection pool.
     */
    public void connect() {
        if (hikariConfig == null) {
            throw new IllegalStateException("Database connection not initialized. Call init() first.");
        }
        hikariDataSource = new HikariDataSource(hikariConfig);
    }

    public static boolean isH2() {
        return System.getProperty("vinject.database", "").equals("h2");
    }

    public static SQLTypeMapper getSQLTypeMapper() {
        return sqlTypeMapper;
    }

    public static String getTablePrefix() {
        return TABLE_PREFIX;
    }

    public static void setTablePrefix(String tablePrefix) {
        TABLE_PREFIX = tablePrefix;
    }

    /**
     * Register a database serializer for a type.
     * 
     * @param type The type being serialized
     * @param serializer The serializer instance
     * @param <T> The type parameter
     */
    public <T> void registerSerializer(Class<T> type, DatabaseSerializer<T> serializer) {
        serializerRegistry.registerSerializer(type, serializer);
    }

    public void initializeEntityMetadata(DependencyContainer dependencyContainer) {
        if (!initialized) {
            return;
        }
        for (Class<?> clazz : dependencyContainer.getAllEntities()) {
            if (clazz.isAnnotationPresent(Entity.class)) {
                Entity entity = clazz.getAnnotation(Entity.class);
                String tableName = entity != null && !entity.table().isEmpty() ? entity.table() : clazz.getSimpleName();
                EntityMetadata metadata = new EntityMetadata(TABLE_PREFIX + tableName, clazz);
                entityMetadataMap.put(clazz, metadata);
            }
        }

        // After all entities are mapped, resolve relationships
        for (EntityMetadata metadata : entityMetadataMap.values()) {
            try {
                metadata.resolveFields(entityMetadataMap, serializerRegistry);
            } catch (Exception e) {
                throw new IllegalStateException("Could not resolve fields for entity: " + metadata.getTableName(), e);
            }
        }
    }

    public void verifyTables() {
        if (this.hikariDataSource == null) {
            return; // No database is used, skip verification
        }

        connect(connection -> {
            for (EntityMetadata metadata : entityMetadataMap.values()) {
                try {
                    if (!DBUtils.tableExists(connection, metadata.getTableName())) {
                        DebugLogger.log(Database.class, "Table does not exist: '" + metadata.getTableName() + "' creating...");
                        // Table doesn't exist; create it
                        createTable(connection, metadata);
                        DebugLogger.log(Database.class, "Table created: " + metadata.getTableName());
                    } else {
                        // Table exists; synchronize columns and relationships
                        DebugLogger.log(Database.class, "Syncing table: " + metadata.getTableName());
                        synchronizeTable(connection, metadata);
                        DebugLogger.log(Database.class, "Table synced: " + metadata.getTableName());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing table " + metadata.getTableName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            // Relationships are synchronized after every table and column exists.
            for (EntityMetadata metadata : entityMetadataMap.values()) {
                synchronizeIndexes(connection, metadata);
            }
            for (EntityMetadata metadata : entityMetadataMap.values()) {
                synchronizeForeignKeys(connection, metadata);
            }
        });
    }

    private void createTable(Connection connection, EntityMetadata metadata) throws Exception {
        StringBuilder createSQL = new StringBuilder(schemaFormatter.formatCreateTablePrefix(metadata.getTableName()))
                .append(" (\n");

        List<String> pkColumns = new ArrayList<>();
        for (FieldMetadata fieldMeta : metadata.getFields()) {
            createSQL.append("  ")
                    .append(schemaFormatter.formatColumnDefinition(fieldMeta.getColumnName(), fieldMeta.getSqlType()))
                    .append(",\n");

            if (fieldMeta.isPrimaryKey()) {
                pkColumns.add(schemaFormatter.formatColumnName(fieldMeta.getColumnName()));
            }
        }

        if (!pkColumns.isEmpty()) {
            createSQL.append("  PRIMARY KEY (")
                    .append(String.join(", ", pkColumns))
                    .append("),\n");
        }

        // remove trailing comma; indexes and foreign keys are created after every table exists
        createSQL.setLength(createSQL.length() - 2);
        createSQL.append("\n");

        createSQL.append(");");

        String sql = schemaFormatter.convertSqlSyntax(createSQL.toString());

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            DebugLogger.log(Database.class, "Created table: " + sql);
        } catch (Exception e) {
            System.err.println("SQL: " + sql);
            System.err.println("Error creating table " + metadata.getTableName() + ": " + e.getMessage());
            throw e;
        }
    }

    private void synchronizeTable(Connection connection, EntityMetadata metadata) throws Exception {
        boolean h2 = isH2();
        Map<String, String> existingColumns = DBUtils.getExistingColumns(connection, metadata.getTableName());
        Map<String, String> entityColumnTypes = new HashMap<>();
        Map<String, FieldMetadata> fieldMetadataMap = new HashMap<>();

        for (FieldMetadata fieldMeta : metadata.getFields()) {
            String name = fieldMeta.getColumnName().toLowerCase(Locale.ENGLISH);
            entityColumnTypes.put(name, fieldMeta.getSqlType());
            fieldMetadataMap.put(name, fieldMeta);
        }

        List<String> addColumns = new ArrayList<>();
        List<String> modifyColumns = new ArrayList<>();
        List<String> dropColumns = new ArrayList<>();

        for (Map.Entry<String, String> entry : entityColumnTypes.entrySet()) {
            String col = entry.getKey();
            String desired = entry.getValue();
            if (!existingColumns.containsKey(col)) {
                addColumns.add(schemaFormatter.formatColumnDefinition(col, desired));
            } else {
                String actual = normalizeColumnDefinitionForComparison(existingColumns.get(col), h2);
                String normalizedDesired = normalizeColumnDefinitionForComparison(desired, h2);
                if (!actual.equals(normalizedDesired)) {
                    modifyColumns.add(schemaFormatter.formatColumnDefinition(col, desired));
                    DebugLogger.log(Database.class, "Type mismatch for column: " + col + " Expected: " + desired + " Actual: " + actual);
                }
            }
        }

        for (String existing : existingColumns.keySet()) {
            if (!entityColumnTypes.containsKey(existing)) {
                dropColumns.add(schemaFormatter.formatColumnName(existing));
            }
        }

        if (addColumns.isEmpty() && modifyColumns.isEmpty() && dropColumns.isEmpty()) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            String alterTablePrefix = schemaFormatter.formatAlterTablePrefix(metadata.getTableName());
            
            if (schemaFormatter.supportsCombinedAlterStatements()) {
                // For MySQL/MariaDB, combine all ALTER statements
                StringBuilder alter = new StringBuilder(alterTablePrefix).append("\n");
                List<String> clauses = new ArrayList<>();
                for (String add : addColumns)    clauses.add("  ADD COLUMN " + add);
                for (String mod : modifyColumns) clauses.add("  MODIFY COLUMN " + mod);
                for (String drop : dropColumns)  clauses.add("  DROP COLUMN " + drop);
                alter.append(String.join(",\n", clauses)).append(";");
                String sql = schemaFormatter.convertSqlSyntax(alter.toString());
                stmt.executeUpdate(sql);
                DebugLogger.log(Database.class, "Alter SQL: " + sql);
            } else {
                // For H2, execute each ALTER statement separately
                for (String clause : addColumns) {
                    String sql = alterTablePrefix + " ADD COLUMN " + clause;
                    sql = schemaFormatter.convertSqlSyntax(sql);
                    stmt.executeUpdate(sql);
                }
                for (String clause : modifyColumns) {
                    String sql = alterTablePrefix + " ALTER COLUMN " + normalizeColumnDefinitionForAlter(clause, h2);
                    sql = schemaFormatter.convertSqlSyntax(sql);
                    stmt.executeUpdate(sql);
                }
                for (String drop : dropColumns) {
                    String sql = alterTablePrefix + " DROP COLUMN " + drop;
                    stmt.executeUpdate(sql);
                }
            }
        }
    }

    private void synchronizeIndexes(Connection connection, EntityMetadata metadata) throws Exception {
        Map<String, DBUtils.IndexInfo> existing = DBUtils.getExistingIndexes(connection, metadata.getTableName());
        try (Statement statement = connection.createStatement()) {
            for (IndexMetadata expected : metadata.getIndexes()) {
                String key = expected.getName().toLowerCase(Locale.ENGLISH);
                DBUtils.IndexInfo actual = existing.get(key);
                if (actual != null && indexMatches(actual, expected)) {
                    continue;
                }
                if (actual != null) {
                    String drop = schemaFormatter.formatDropIndex(metadata.getTableName(), actual.name());
                    statement.executeUpdate(drop);
                    DebugLogger.log(Database.class, "Dropped mismatched index: " + drop);
                }
                String create = schemaFormatter.formatCreateIndex(
                        metadata.getTableName(), expected.getName(), expected.getColumns(), expected.isUnique());
                statement.executeUpdate(create);
                DebugLogger.log(Database.class, "Created index: " + create);
            }
        }
    }

    private void synchronizeForeignKeys(Connection connection, EntityMetadata metadata) throws Exception {
        Map<String, DBUtils.ForeignKeyInfo> existing = DBUtils.getExistingForeignKeys(connection, metadata.getTableName());
        try (Statement statement = connection.createStatement()) {
            for (FieldMetadata field : metadata.getFields()) {
                if (!field.isForeignKey()) continue;
                ForeignKeyMetadata expected = field.getForeignKey();
                String key = expected.getName().toLowerCase(Locale.ENGLISH);
                DBUtils.ForeignKeyInfo actual = existing.get(key);
                if (actual != null && foreignKeyMatches(actual, field.getColumnName(), expected)) {
                    continue;
                }
                if (actual != null) {
                    String drop = schemaFormatter.formatDropForeignKey(metadata.getTableName(), actual.name());
                    statement.executeUpdate(drop);
                    DebugLogger.log(Database.class, "Dropped mismatched foreign key: " + drop);
                }
                String create = schemaFormatter.formatAddForeignKey(
                        metadata.getTableName(), expected.getName(), field.getColumnName(),
                        expected.getReferencedTable(), expected.getReferencedColumn(),
                        expected.getOnDelete(), expected.getOnUpdate());
                statement.executeUpdate(create);
                DebugLogger.log(Database.class, "Created foreign key: " + create);
            }
        }
    }

    private static boolean indexMatches(DBUtils.IndexInfo actual, IndexMetadata expected) {
        if (actual.unique() != expected.isUnique() || actual.columns().size() != expected.getColumns().size()) {
            return false;
        }
        for (int i = 0; i < actual.columns().size(); i++) {
            if (!actual.columns().get(i).equalsIgnoreCase(expected.getColumns().get(i))) return false;
        }
        return true;
    }

    private static boolean foreignKeyMatches(DBUtils.ForeignKeyInfo actual, String localColumn,
                                             ForeignKeyMetadata expected) {
        return actual.column().equalsIgnoreCase(localColumn)
                && actual.referencedTable().equalsIgnoreCase(expected.getReferencedTable())
                && actual.referencedColumn().equalsIgnoreCase(expected.getReferencedColumn())
                && actionsEquivalent(actual.onDelete(), expected.getOnDelete())
                && actionsEquivalent(actual.onUpdate(), expected.getOnUpdate());
    }

    private static boolean actionsEquivalent(ForeignKeyAction actual, ForeignKeyAction expected) {
        if (actual == expected) return true;
        return (actual == ForeignKeyAction.NO_ACTION || actual == ForeignKeyAction.RESTRICT)
                && (expected == ForeignKeyAction.NO_ACTION || expected == ForeignKeyAction.RESTRICT);
    }

    private static String normalizeColumnDefinitionForComparison(String definition, boolean h2) {
        if (definition == null) {
            return "";
        }
        String normalized = definition.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ENGLISH);
        if (h2) {
            normalized = UNIQUE_TOKEN_PATTERN.matcher(normalized).replaceAll("");
        }
        return normalized.trim();
    }

    private static String normalizeColumnDefinitionForAlter(String definition, boolean h2) {
        if (definition == null) {
            return "";
        }
        if (!h2) {
            return definition;
        }
        return UNIQUE_TOKEN_PATTERN.matcher(definition).replaceAll("").trim();
    }

    public boolean isConnected() {
        return hikariDataSource != null;
    }

    @Override
    public Connection getConnection() throws Exception {
        return hikariDataSource.getConnection();
    }

    @Override
    public void connect(VoidConnection connection) {
        try (Connection conn = hikariDataSource.getConnection()) {
            connection.connect(conn);
        } catch (Exception e) {
            throw new RuntimeException("Database connection error", e);
        }
    }

    @Override
    public <T> T connect(ConnectionResult<T> connection) {
        try (Connection conn = hikariDataSource.getConnection()) {
            return connection.connect(conn);
        } catch (Exception e) {
            throw new RuntimeException("Database connection error", e);
        }
    }

    @Override
    public void transaction(VoidConnection connection) {
        try (Connection conn = hikariDataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                connection.connect(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            throw new RuntimeException("Transaction error", e);
        }
    }

    @Override
    public <T> T transaction(ConnectionResult<T> connection) {
        try (Connection conn = hikariDataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                T result = connection.connect(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            throw new RuntimeException("Transaction error", e);
        }
    }

    public void shutdown() {
        try {
            if (this.hikariDataSource != null) {
                this.hikariDataSource.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
