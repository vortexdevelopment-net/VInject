package net.vortexdevelopment.vinject.database.formatter;

/**
 * Schema formatter implementation for MySQL and MariaDB databases.
 * Uses backticks for identifiers.
 */
public class MySQLSchemaFormatter implements SchemaFormatter {

    @Override
    public String formatTableName(String tableName) {
        return "`" + tableName + "`";
    }

    @Override
    public String formatColumnName(String columnName) {
        return "`" + columnName + "`";
    }

    @Override
    public String formatColumnDefinition(String columnName, String sqlType) {
        return formatColumnName(columnName) + " " + sqlType;
    }

    @Override
    public String convertSqlSyntax(String sql) {
        // Add ENGINE=InnoDB for MySQL/MariaDB if it's a CREATE TABLE statement
        if (sql.toUpperCase().startsWith("CREATE TABLE")) {
            // Remove trailing semicolon if it exists to append engine
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1);
            }
            return sql + " ENGINE=InnoDB;";
        }
        return sql;
    }

    @Override
    public String formatAlterTablePrefix(String tableName) {
        return "ALTER TABLE " + formatTableName(tableName);
    }

    @Override
    public String formatCreateTablePrefix(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + formatTableName(tableName);
    }

    @Override
    public boolean supportsCombinedAlterStatements() {
        return true; // MySQL/MariaDB supports combining multiple ALTER operations
    }
}
