package net.vortexdevelopment.vinject.database.formatter;

import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;

import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public String formatCreateIndex(String tableName, String indexName, List<String> columns, boolean unique) {
        return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + formatColumnName(indexName)
                + " ON " + formatTableName(tableName) + " ("
                + columns.stream().map(this::formatColumnName).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public String formatDropIndex(String tableName, String indexName) {
        return "DROP INDEX " + formatColumnName(indexName) + " ON " + formatTableName(tableName);
    }

    @Override
    public String formatAddForeignKey(String tableName, String constraintName, String columnName,
                                      String referencedTable, String referencedColumn,
                                      ForeignKeyAction onDelete, ForeignKeyAction onUpdate) {
        return "ALTER TABLE " + formatTableName(tableName) + " ADD CONSTRAINT " + formatColumnName(constraintName)
                + " FOREIGN KEY (" + formatColumnName(columnName) + ") REFERENCES "
                + formatTableName(referencedTable) + " (" + formatColumnName(referencedColumn) + ")"
                + " ON DELETE " + onDelete.sql() + " ON UPDATE " + onUpdate.sql();
    }

    @Override
    public String formatDropForeignKey(String tableName, String constraintName) {
        return "ALTER TABLE " + formatTableName(tableName) + " DROP FOREIGN KEY " + formatColumnName(constraintName);
    }
}
