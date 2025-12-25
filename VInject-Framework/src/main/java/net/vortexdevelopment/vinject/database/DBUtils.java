package net.vortexdevelopment.vinject.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DBUtils {

    /**
     * Retrieves existing columns for a given table.
     *
     * @param connection The database connection.
     * @param tableName  The name of the table.
     * @return A map of column names to their SQL types.
     * @throws Exception If a database access error occurs.
     */
    public static Map<String, String> getExistingColumns(Connection connection, String tableName) throws Exception {
        // Detect database type from connection metadata for reliability
        DatabaseMetaData metaData = connection.getMetaData();
        String databaseProductName = metaData.getDatabaseProductName().toLowerCase(Locale.ENGLISH);
        boolean isH2 = databaseProductName.contains("h2");
        Map<String, String> columns = new HashMap<>();
        String sql;
        if (isH2) {
            sql = "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_DEFAULT, IS_IDENTITY " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ?";
        } else {
            sql = "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, EXTRA, IS_NULLABLE " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE()";
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH);
                    String columnType;
                    String columnDefault = rs.getString("COLUMN_DEFAULT");
                    String isNullable = rs.getString("IS_NULLABLE");
                    String extra = "";

                    if (isH2) {
                        String typeName = rs.getString("DATA_TYPE").toUpperCase(Locale.ENGLISH);
                        int maxLength = rs.getInt("CHARACTER_MAXIMUM_LENGTH");

                        if (maxLength > 0 && (typeName.contains("CHAR") || typeName.contains("BINARY"))) {
                            columnType = typeName + "(" + maxLength + ")";
                        } else {
                            columnType = typeName;
                        }
                    } else {
                        columnType = rs.getString("COLUMN_TYPE").toUpperCase(Locale.ENGLISH);
                        extra = rs.getString("EXTRA").toUpperCase(Locale.ENGLISH);
                    }

                    StringBuilder typeDefinition = new StringBuilder(columnType);

                    if (isH2 && columnType.equals("ENUM")) {
                        //Query enum values
                        typeDefinition.insert(4, getEnumValues(connection, tableName, columnName));
                    }

                    if ("NO".equalsIgnoreCase(isNullable)) {
                        typeDefinition.append(" NOT NULL");
                    } else {
                        typeDefinition.append(" NULL");
                    }

                    if (isH2) {
                        String isIdentity = rs.getString("IS_IDENTITY");
                        if ("YES".equalsIgnoreCase(isIdentity)) {
                            typeDefinition.append(" AUTO_INCREMENT");
                        }
                    }

                    if (columnDefault != null) {
                        if (columnDefault.contains(".")) {
                            columnDefault = columnDefault.replaceAll("\\.0+$", "");
                        }
                        typeDefinition.append(" DEFAULT ").append(columnDefault);
                    }

                    if (!extra.isEmpty()) {
                        typeDefinition.append(" ").append(extra);
                    }

                    columns.put(columnName, typeDefinition.toString());
                }
            }
        }
        return columns;
    }

    private static int getEnumIdentifier(Connection connection, String tableName, String columnName) {
        String sql = "SELECT DTD_IDENTIFIER FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName.toUpperCase());
            ps.setString(2, columnName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("DTD_IDENTIFIER");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // Not found or error
    }

    public static String getEnumValues(Connection connection, String tableName, String columnName) {
        int enumIdentifier = getEnumIdentifier(connection, tableName, columnName);

        String sql = "SELECT VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES " +
                "WHERE OBJECT_NAME = ? AND ENUM_IDENTIFIER = ? ORDER BY VALUE_ORDINAL";
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName.toUpperCase());
            ps.setInt(2, enumIdentifier);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add("'" + rs.getString("VALUE_NAME") + "'");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "(" + String.join(",", values) + ")";
    }





    /**
     * Checks if a table exists in the database.
     *
     * @param connection The database connection.
     * @param tableName  The name of the table.
     * @return True if the table exists, false otherwise.
     * @throws Exception If a database access error occurs.
     */
    public static boolean tableExists(Connection connection, String tableName) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

}
