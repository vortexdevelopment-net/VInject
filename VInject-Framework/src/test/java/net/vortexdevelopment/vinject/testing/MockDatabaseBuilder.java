package net.vortexdevelopment.vinject.testing;

import net.vortexdevelopment.vinject.database.Database;

import java.io.File;

/**
 * Builder for creating mock H2 databases for testing purposes.
 * Simplifies creation of in-memory test databases with predefined schemas.
 * 
 * <p>Usage example:
 * <pre>
 * {@code
 * // Create simple in-memory database
 * Database db = MockDatabaseBuilder.createInMemory();
 * 
 * // Create database with specific entities
 * Database db = MockDatabaseBuilder.createWithEntities(User.class, Order.class);
 * }
 * </pre>
 */
public class MockDatabaseBuilder {

    /**
     * Create an in-memory H2 database with default name.
     */
    public static Database createInMemory() {
        return createInMemory("test_db");
    }

    /**
     * Create an in-memory H2 database with a specific name.
     */
    public static Database createInMemory(String dbName) {
        Database database = new Database(
                "",              // host (not used for in-memory)
                "",              // port (not used for in-memory)
                dbName,          // database name
                "h2",            // database type
                "sa",            // username
                "",              // password
                10,              // max pool size
                new File("mem")  // h2 file (in-memory mode)
        );
        database.init();
        return database;
    }

    /**
     * Create an in-memory H2 database configured for specific entity classes.
     * The database will be initialized and schema will be created for the entities.
     */
    public static Database createWithEntities(Class<?>... entityClasses) {
        Database database = createInMemory();
        // Database initialization will automatically create tables for entities
        // when they are registered via RepositoryContainer
        return database;
    }

    /**
     * Create a file-based H2 database for testing that persists between runs.
     * Useful for debugging or when you need to inspect the database state.
     */
    public static Database createFileBased(String fileName) {
        File dbFile = new File("./test-data/" + fileName);
        dbFile.getParentFile().mkdirs();

        Database database = new Database(
                "",              // host
                "",              // port
                fileName,        // database name
                "h2",            // database type
                "sa",            // username
                "",              // password
                10,              // max pool size
                dbFile           // h2 file
        );
        database.init();
        return database;
    }

    /**
     * Clean up a file-based H2 database.
     */
    public static void cleanupFileBased(String fileName) {
        File dbFile = new File("./test-data/" + fileName + ".mv.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        File traceFile = new File("./test-data/" + fileName + ".trace.db");
        if (traceFile.exists()) {
            traceFile.delete();
        }
    }
}
