package net.vortexdevelopment.vinject.annotation.database;

/**
 * Action applied by the database when a referenced key is deleted or updated.
 */
public enum ForeignKeyAction {
    NO_ACTION("NO ACTION"),
    RESTRICT("RESTRICT"),
    CASCADE("CASCADE"),
    SET_NULL("SET NULL");

    private final String sql;

    ForeignKeyAction(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
