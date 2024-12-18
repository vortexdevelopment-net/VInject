package net.vortexdevelopment.vinject.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
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
        Map<String, String> columns = new HashMap<>();

        // Query the INFORMATION_SCHEMA for more detailed column info
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_DEFAULT, EXTRA " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_NAME = '" + tableName + "' AND TABLE_SCHEMA = DATABASE()";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME").toLowerCase();
                String columnType = rs.getString("COLUMN_TYPE"); // Includes type + length (e.g., VARCHAR(255), DECIMAL(10,2))
                String columnDefault = rs.getString("COLUMN_DEFAULT"); // Default value (e.g., CURRENT_TIMESTAMP)
                String extra = rs.getString("EXTRA").toUpperCase(Locale.ENGLISH); // Includes additional info like ON UPDATE CURRENT_TIMESTAMP

                // Build the full type definition
                StringBuilder typeDefinition = new StringBuilder(columnType.toUpperCase(Locale.ENGLISH));
                if (columnDefault != null) {
                    typeDefinition.append(" DEFAULT ").append(columnDefault);
                }
                if (extra != null && !extra.isEmpty()) {
                    typeDefinition.append(" ").append(extra);
                }

                // Add to the map
                columns.put(columnName, typeDefinition.toString());
            }
        }
        return columns;
    }


    /**
     * Checks if a table exists in the database.
     *
     * @param connection The database connection.
     * @param tableName  The name of the table.
     * @return True if the table exists, false otherwise.
     * @throws Exception If a database access error occurs.
     */
    public static boolean tableExists(Connection connection, String databaseName, String tableName) throws Exception {
        PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?");
        ps.setString(1, databaseName);
        ps.setString(2, tableName);
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }
}
