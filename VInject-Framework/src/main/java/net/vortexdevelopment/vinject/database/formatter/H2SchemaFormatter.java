package net.vortexdevelopment.vinject.database.formatter;

/**
 * Schema formatter implementation for H2 database.
 * Uses double quotes for identifiers and converts MySQL-specific syntax.
 */
public class H2SchemaFormatter implements SchemaFormatter {

    @Override
    public String formatTableName(String tableName) {
        return "\"" + tableName + "\"";
    }

    @Override
    public String formatColumnName(String columnName) {
        return "\"" + columnName + "\"";
    }

    @Override
    public String formatColumnDefinition(String columnName, String sqlType) {
        return formatColumnName(columnName) + " " + sqlType;
    }

    @Override
    public String convertSqlSyntax(String sql) {
        // Convert AUTO_INCREMENT to IDENTITY for H2
        return sql.replaceAll("(?i)AUTO_INCREMENT", "IDENTITY");
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
        return false; // H2 requires separate ALTER TABLE statements
    }
}
