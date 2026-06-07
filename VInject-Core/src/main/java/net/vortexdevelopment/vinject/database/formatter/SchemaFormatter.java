package net.vortexdevelopment.vinject.database.formatter;

/**
 * Interface for formatting database schema elements (table names, column names, SQL statements)
 * according to database-specific syntax rules.
 */
public interface SchemaFormatter {

    /**
     * Formats a table name with appropriate quoting for the database type.
     *
     * @param tableName the table name to format
     * @return the formatted table name with appropriate quotes
     */
    String formatTableName(String tableName);

    /**
     * Formats a column name with appropriate quoting for the database type.
     *
     * @param columnName the column name to format
     * @return the formatted column name with appropriate quotes
     */
    String formatColumnName(String columnName);

    /**
     * Formats a column definition (column name + SQL type) for use in CREATE/ALTER TABLE statements.
     *
     * @param columnName the column name
     * @param sqlType the SQL type definition
     * @return the formatted column definition
     */
    String formatColumnDefinition(String columnName, String sqlType);

    /**
     * Converts database-specific SQL syntax in a statement (e.g., AUTO_INCREMENT to IDENTITY for H2).
     *
     * @param sql the SQL statement to convert
     * @return the converted SQL statement
     */
    String convertSqlSyntax(String sql);

    /**
     * Formats the beginning of an ALTER TABLE statement.
     *
     * @param tableName the table name
     * @return the formatted ALTER TABLE statement prefix
     */
    String formatAlterTablePrefix(String tableName);

    /**
     * Formats the beginning of a CREATE TABLE statement.
     *
     * @param tableName the table name
     * @return the formatted CREATE TABLE statement prefix
     */
    String formatCreateTablePrefix(String tableName);

    /**
     * Returns whether this formatter supports combining multiple ALTER TABLE operations in a single statement.
     *
     * @return true if multiple ALTER operations can be combined, false if they must be executed separately
     */
    boolean supportsCombinedAlterStatements();
}
