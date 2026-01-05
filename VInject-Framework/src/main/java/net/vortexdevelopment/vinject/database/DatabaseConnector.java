package net.vortexdevelopment.vinject.database;

import java.sql.Connection;

public interface DatabaseConnector {

    Connection getConnection() throws Exception;

    /**
     * Executes an operation with auto-commit enabled (no manual transaction).
     */
    void connect(VoidConnection connection);

    /**
     * Executes a query with auto-commit enabled and returns a result.
     */
    <T> T connect(ConnectionResult<T> connection);

    /**
     * Executes a transactional operation (auto-commit false, commit, rollback on error).
     */
    void transaction(VoidConnection connection);

    /**
     * Executes a transactional operation and returns a result.
     */
    <T> T transaction(ConnectionResult<T> connection);

    static interface VoidConnection {

        void connect(Connection connection) throws Exception;

    }

    static interface ConnectionResult<T> {

        T connect(Connection connection) throws Exception;

    }
}
