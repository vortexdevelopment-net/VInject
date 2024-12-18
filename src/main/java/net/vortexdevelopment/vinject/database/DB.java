package net.vortexdevelopment.vinject.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.database.meta.EntityMetadata;
import net.vortexdevelopment.vinject.database.meta.FieldMetadata;
import net.vortexdevelopment.vinject.database.meta.ForeignKeyMetadata;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

public class DB implements DatabaseConnector {

    private HikariDataSource hikari;
    private Map<Class<?>, EntityMetadata> entityMetadataMap = new HashMap<>();
    private static String TABLE_PREFIX = "example_";
    private final List<String> FOREIGN_KEY_QUERIES = new ArrayList<>();

    public DB(DependencyContainer dependencyContainer) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/plugincore?autoReconnect=true&useSSL=false");
        config.setUsername("Admin");
        config.setPassword("pass");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari = new HikariDataSource(config);
        initializeEntityMetadata(dependencyContainer);
        verifyTables();
    }

    public static String getTablePrefix() {
        return TABLE_PREFIX;
    }

    private void initializeEntityMetadata(DependencyContainer dependencyContainer) {
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
            metadata.resolveFields(entityMetadataMap);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void verifyTables() {
        connect(connection -> {
            for (EntityMetadata metadata : entityMetadataMap.values()) {
                try {
                    if (!DBUtils.tableExists(connection, "plugincore", metadata.getTableName())) {
                        // Table doesn't exist; create it
                        createTable(connection, metadata);
                        System.err.println("Table does not exist: " + metadata.getTableName());
                    } else {
                        // Table exists; synchronize columns and relationships
                        synchronizeTable(connection, metadata);
                        System.err.println("Table exists: " + metadata.getTableName());
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
        StringBuilder createSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS `")
                .append(metadata.getTableName()).append("` (\n");
        List<String> pkColumns = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();

        for (FieldMetadata fieldMeta : metadata.getFields()) {
            createSQL.append("  `").append(fieldMeta.getColumnName()).append("` ")
                    .append(fieldMeta.getSqlType());

            if (!fieldMeta.isNullable()) {
                createSQL.append(" NOT NULL");
            }

            if (fieldMeta.isUnique()) {
                createSQL.append(" UNIQUE");
            }

            if (fieldMeta.isAutoIncrement()) {
                createSQL.append(" AUTO_INCREMENT");
            }

            createSQL.append(",\n");

            if (fieldMeta.isPrimaryKey()) {
                pkColumns.add("`" + fieldMeta.getColumnName() + "`");
            }

            if (fieldMeta.isForeignKey()) {
                //Check if referenced table exists
                if (!DBUtils.tableExists(connection, "plugincore", fieldMeta.getForeignKey().getReferencedTable())) {
                    //Add to foreign key queries
                    FOREIGN_KEY_QUERIES.add("ALTER TABLE `" + metadata.getTableName() + "` ADD FOREIGN KEY (`" + fieldMeta.getColumnName() + "`) REFERENCES `" + fieldMeta.getForeignKey().getReferencedTable() + "`(`" + fieldMeta.getForeignKey().getReferencedColumn() + "`)");
                } else {
                    ForeignKeyMetadata fk = fieldMeta.getForeignKey();
                    foreignKeys.add("  FOREIGN KEY (`" + fieldMeta.getColumnName() + "`) REFERENCES `"
                            + fk.getReferencedTable() + "`(`" + fk.getReferencedColumn() + "`)");
                }
            }
        }

        // Primary Key
        if (!pkColumns.isEmpty()) {
            createSQL.append("  PRIMARY KEY (")
                    .append(String.join(", ", pkColumns))
                    .append("),\n");
        }

        // Foreign Keys
        if (!foreignKeys.isEmpty()) {
            createSQL.append(String.join(",\n", foreignKeys)).append("\n");
        } else {
            // Remove the last comma and newline
            createSQL.setLength(createSQL.length() - 2);
            createSQL.append("\n");
        }

        createSQL.append(") ENGINE=InnoDB;");

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createSQL.toString());
            System.out.println("Created table: " + metadata.getTableName());
        } catch (Exception e) {
            System.err.println("SQL: " + createSQL.toString());
            System.err.println("Error creating table " + metadata.getTableName() + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private void synchronizeTable(Connection connection, EntityMetadata metadata) throws Exception {
        // Retrieve existing columns from DB
        Map<String, String> existingColumns = DBUtils.getExistingColumns(connection, metadata.getTableName());
        Map<String, String> entityColumnTypes = new HashMap<>();
        Map<String, FieldMetadata> fieldMetadataMap = new HashMap<>();

        for (FieldMetadata fieldMeta : metadata.getFields()) {
            entityColumnTypes.put(fieldMeta.getColumnName().toLowerCase(), fieldMeta.getSqlType());
            fieldMetadataMap.put(fieldMeta.getColumnName().toLowerCase(), fieldMeta);
        }

        List<String> addColumns = new ArrayList<>();
        List<String> modifyColumns = new ArrayList<>();
        List<String> dropColumns = new ArrayList<>();
        List<String> addForeignKeys = new ArrayList<>();

        // Determine columns to add or modify
        for (Map.Entry<String, String> entry : entityColumnTypes.entrySet()) {
            String columnName = entry.getKey();
            String desiredType = entry.getValue();

            if (!existingColumns.containsKey(columnName)) {
                // Column doesn't exist; add it
                FieldMetadata fieldMeta = fieldMetadataMap.get(columnName);
                addColumns.add("`" + fieldMeta.getColumnName() + "` " + desiredType);

                // Handle foreign keys if any
                if (fieldMeta.isForeignKey()) {
                    ForeignKeyMetadata fk = fieldMeta.getForeignKey();
                    addForeignKeys.add("  ADD FOREIGN KEY (`" + fieldMeta.getColumnName() + "`) REFERENCES `"
                            + fk.getReferencedTable() + "`(`" + fk.getReferencedColumn() + "`)");
                }
            } else {
                String existingType = existingColumns.get(columnName).toUpperCase();
                // You might need a more sophisticated type comparison
                if (!existingType.equals(desiredType.toUpperCase())) {
                    FieldMetadata fieldMeta = fieldMetadataMap.get(columnName);
                    modifyColumns.add("`" + fieldMeta.getColumnName() + "` " + desiredType);
                }
            }
        }

        // Determine columns to drop
        for (String existingColumn : existingColumns.keySet()) {
            if (!entityColumnTypes.containsKey(existingColumn)) {
                dropColumns.add("`" + existingColumn + "`");
            }
        }

        // Execute ALTER statements if needed
        if (!addColumns.isEmpty() || !modifyColumns.isEmpty() || !dropColumns.isEmpty() || !addForeignKeys.isEmpty()) {
            StringBuilder alterSQL = new StringBuilder("ALTER TABLE `")
                    .append(metadata.getTableName())
                    .append("`\n");

            List<String> alterations = new ArrayList<>();

            for (String add : addColumns) {
                System.err.println("Adding column: " + add);
                alterations.add("  ADD COLUMN " + add);
            }

            for (String modify : modifyColumns) {
                alterations.add("  MODIFY COLUMN " + modify);
            }

            for (String drop : dropColumns) {
                alterations.add("  DROP COLUMN " + drop);
            }

            alterations.addAll(addForeignKeys);

            alterSQL.append(String.join(",\n", alterations)).append(";");

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(alterSQL.toString());
                System.err.println("SQL: " + alterSQL.toString());
                System.out.println("Synchronized table: " + metadata.getTableName());
            } catch (Exception e) {
                System.err.println("SQL: " + alterSQL.toString());
                System.err.println("Error synchronizing table " + metadata.getTableName() + ": " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } else {
            System.out.println("Table already synchronized: " + metadata.getTableName());
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

//    public void connect(Consumer<Connection> callback) {
//        try (Connection conn = hikari.getConnection()) {
//            callback.accept(conn);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public <T> T connect(Consumer<Connection> callback) {
//        try (Connection conn = hikari.getConnection()) {
//            return callback.accept(conn);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

    @Override
    public Connection getConnection() throws Exception {
        return hikari.getConnection();
    }

    @Override
    public void connect(VoidConnection connection) {
        try (Connection conn = hikari.getConnection()) {
            connection.connect(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public <T> T connect(ConnectionResult<T> connection) {
        try (Connection conn = hikari.getConnection()) {
            return connection.connect(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
