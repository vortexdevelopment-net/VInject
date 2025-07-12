package net.vortexdevelopment.vinject.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.database.mapper.H2Mapper;
import net.vortexdevelopment.vinject.database.mapper.MySQLMapper;
import net.vortexdevelopment.vinject.database.meta.EntityMetadata;
import net.vortexdevelopment.vinject.database.meta.FieldMetadata;
import net.vortexdevelopment.vinject.database.meta.ForeignKeyMetadata;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

public class Database implements DatabaseConnector {

    HikariConfig hikariConfig;
    private HikariDataSource hikariDataSource;
    private Map<Class<?>, EntityMetadata> entityMetadataMap = new HashMap<>();
    private static String TABLE_PREFIX = "example_";
    private final List<String> FOREIGN_KEY_QUERIES = new ArrayList<>();
    private String database;
    private static SQLTypeMapper sqlTypeMapper;

    public Database(String host, String port, String database, String type, String username, String password, int maxPoolSize, File h2File) {
        hikariConfig = new HikariConfig();

        if (type.equals("h2")) {
            //Set system propery vinject.database=h2
            sqlTypeMapper = new H2Mapper();
            System.setProperty("vinject.database", "h2");
            hikariConfig.setDriverClassName("org.h2.Driver");
            hikariConfig.setJdbcUrl("jdbc:h2:file:./" + h2File.getPath().replaceAll("\\\\", "/") + ";AUTO_RECONNECT=TRUE;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        } else {
            sqlTypeMapper = new MySQLMapper();
            hikariConfig.setJdbcUrl("jdbc:" + type + "://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false");
        }

        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.database = database;
    }

    public static boolean isH2() {
        return System.getProperty("vinject.database", "").equals("h2");
    }

    public static SQLTypeMapper getSQLTypeMapper() {
        return sqlTypeMapper;
    }

    public void init() {
        System.out.println("Initializing database connection...");
        hikariDataSource = new HikariDataSource(hikariConfig);
        System.out.println("Database connection initialized.");

        //Init metadata
        initializeEntityMetadata(DependencyContainer.getInstance());

        System.out.println("Verifying tables...");
        verifyTables();
        System.out.println("Tables verified.");
    }

    public static String getTablePrefix() {
        return TABLE_PREFIX;
    }

    public static void setTablePrefix(String tablePrefix) {
        TABLE_PREFIX = tablePrefix;
    }

    public void initializeEntityMetadata(DependencyContainer dependencyContainer) {
        Class<?>[] classes = dependencyContainer.getAllEntities();
        for (Class<?> clazz : classes) {
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
                metadata.resolveFields(entityMetadataMap);
            } catch (Exception e) {
                System.err.println("Could not resolve fields for entity: " + metadata.getTableName());
                e.printStackTrace();
            }
        }
    }

    private void verifyTables() {
        boolean isH2 = System.getProperty("vinject.database", "").equals("h2");
        connect(connection -> {
            for (EntityMetadata metadata : entityMetadataMap.values()) {
                try {
                    if (!DBUtils.tableExists(connection, metadata.getTableName())) {
                        System.out.println("Table does not exist: '" + metadata.getTableName() + "' creating...");
                        // Table doesn't exist; create it
                        createTable(connection, metadata);
                        System.out.println("Table created: " + metadata.getTableName());
                    } else {
                        // Table exists; synchronize columns and relationships
                        System.out.println("Syncing table: " + metadata.getTableName());
                        synchronizeTable(connection, metadata);
                        System.out.println("Table synced: " + metadata.getTableName());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing table " + metadata.getTableName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            // Create foreign keys after all tables are created
            for (String query : FOREIGN_KEY_QUERIES) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(query);
                    System.out.println("Created foreign key: " + query);
                } catch (Exception e) {
                    System.err.println("Error creating foreign key: " + query + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    private void createTable(Connection connection, EntityMetadata metadata) throws Exception {
        boolean isH2 = isH2();
        StringBuilder createSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS `")
                .append(metadata.getTableName())
                .append("` (\n");

        List<String> pkColumns = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();

        for (FieldMetadata fieldMeta : metadata.getFields()) {
            createSQL.append("  `")
                    .append(fieldMeta.getColumnName())
                    .append("` ")
                    .append(fieldMeta.getSqlType())
                    .append(",\n");

            if (fieldMeta.isPrimaryKey()) {
                pkColumns.add("`" + fieldMeta.getColumnName() + "`");
            }
            // foreign key handling omitted…
        }

        if (!pkColumns.isEmpty()) {
            createSQL.append("  PRIMARY KEY (")
                    .append(String.join(", ", pkColumns))
                    .append("),\n");
        }

        if (!foreignKeys.isEmpty()) {
            createSQL.append(String.join(",\n", foreignKeys)).append("\n");
        } else {
            // remove trailing comma
            createSQL.setLength(createSQL.length() - 2);
            createSQL.append("\n");
        }

        createSQL.append(") ENGINE=InnoDB;");

        String sql = createSQL.toString();
        if (isH2) {
            sql = sql.replace("`", "");
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("Created table: " + sql);
        } catch (Exception e) {
            System.err.println("SQL: " + sql);
            System.err.println("Error creating table " + metadata.getTableName() + ": " + e.getMessage());
            throw e;
        }
    }

    private void synchronizeTable(Connection connection, EntityMetadata metadata) throws Exception {
        boolean isH2 = Database.isH2();
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
                addColumns.add(isH2 ? col + " " + desired : "`" + col + "` " + desired);
            } else {
                String actual = existingColumns.get(col).toUpperCase(Locale.ENGLISH);
                if (!actual.equals(desired.toUpperCase(Locale.ENGLISH))) {
                    modifyColumns.add(isH2 ? col + " " + desired : "`" + col + "` " + desired);
                    System.out.println("Type mismatch for column: " + col + " Expected: " + desired + " Actual: " + actual);
                }
            }
        }

        for (String existing : existingColumns.keySet()) {
            if (!entityColumnTypes.containsKey(existing)) {
                dropColumns.add("`" + existing + "`");
            }
        }

        if (addColumns.isEmpty() && modifyColumns.isEmpty() && dropColumns.isEmpty()) {
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            if (isH2) {
                for (String clause : addColumns) {
                    String sql = "ALTER TABLE " + metadata.getTableName()
                            + " ADD COLUMN " + clause.replaceAll("`", "")
                            .replaceAll("(?i)AUTO_INCREMENT", "IDENTITY");
                    stmt.executeUpdate(sql);
                }
                for (String clause : modifyColumns) {
                    String sql = "ALTER TABLE " + metadata.getTableName()
                            + " ALTER COLUMN " + clause.replaceAll("`", "")
                            .replaceAll("(?i)AUTO_INCREMENT", "IDENTITY");
                    stmt.executeUpdate(sql);
                }
                for (String drop : dropColumns) {
                    String col = drop.replaceAll("`", "");
                    String sql = "ALTER TABLE " + metadata.getTableName() + " DROP COLUMN " + col;
                    stmt.executeUpdate(sql);
                }
            } else {
                StringBuilder alter = new StringBuilder("ALTER TABLE `")
                        .append(metadata.getTableName()).append("`\n");
                List<String> clauses = new ArrayList<>();
                for (String add : addColumns)    clauses.add("  ADD COLUMN " + add);
                for (String mod : modifyColumns) clauses.add("  MODIFY COLUMN " + mod);
                for (String drop : dropColumns)  clauses.add("  DROP COLUMN " + drop);
                alter.append(String.join(",\n", clauses)).append(";");
                stmt.executeUpdate(alter.toString());
                System.out.println("Alter SQL: " + alter);
            }
        }
    }

    private String getOriginalColumnName(Map<Field, Column> columns, String lowerCaseName) {
        for (Map.Entry<Field, Column> entry : columns.entrySet()) {
            String columnName = !entry.getValue().name().isEmpty() ? entry.getValue().name().toLowerCase() : entry.getKey().getName().toLowerCase();
            if (columnName.equals(lowerCaseName)) {
                return !entry.getValue().name().isEmpty() ? entry.getValue().name() : entry.getKey().getName();
            }
        }
        return lowerCaseName; // Fallback
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
            e.printStackTrace();
        }
    }

    @Override
    public <T> T connect(ConnectionResult<T> connection) {
        try (Connection conn = hikariDataSource.getConnection()) {
            return connection.connect(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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
