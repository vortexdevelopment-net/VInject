package net.vortexdevelopment.vinject.testing;

import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for testing repository interfaces and database operations.
 * Provides helpers for creating repositories, verifying query methods,
 * and managing test data.
 */
public class RepositoryTestUtils {

    /**
     * Create a repository instance with a mock database.
     * This creates a repository using the RepositoryContainer directly.
     * 
     * Note: For full repository testing, use TestApplicationContext which handles
     * repository registration properly through the DependencyContainer.
     */
    @SuppressWarnings("unchecked")
    public static <T> T createRepository(Class<T> repoInterface, Database db) {
        // Create a RepositoryContainer
        RepositoryContainer container = new RepositoryContainer(db);
        
        // Find the entity class from the repository interface's generic type
        // This is a simplified approach - in real usage, TestApplicationContext should be used
        throw new UnsupportedOperationException(
            "Direct repository creation is not supported. " +
            "Please use TestApplicationContext.builder().withMockDatabase()...build() instead."
        );
    }

    /**
     * Verify that a query method signature is valid.
     * This checks if the method follows the naming conventions (findBy*, save, delete, etc.).
     */
    public static boolean verifyQueryMethodSignature(Method method) {
        String methodName = method.getName();
        
        // Check for common repository method patterns
        return methodName.startsWith("findBy") ||
               methodName.startsWith("findTopBy") ||
               methodName.startsWith("findAllBy") ||
               methodName.equals("save") ||
               methodName.equals("delete") ||
               methodName.equals("deleteById") ||
               methodName.equals("findById") ||
               methodName.equals("findAll") ||
               methodName.equals("count");
    }

    /**
     * Clear all data from all tables in the database.
     * Useful for cleaning up between tests.
     */
    public static void clearDatabase(Database db) {
        try (Connection conn = db.getConnection()) {
            List<String> tables = getTableNames(conn);
            
            // If database is empty, nothing to clear
            if (tables.isEmpty()) {
                return;
            }
            
            Statement stmt = conn.createStatement();
            // Disable foreign key checks
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            
            // Clear all tables (ignore errors for tables that don't actually exist)
            for (String table : tables) {
                try {
                    stmt.execute("TRUNCATE TABLE " + table);
                } catch (Exception e) {
                    // Ignore - table might not actually exist despite being in metadata
                }
            }
            
            // Re-enable foreign key checks
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            
            stmt.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear database", e);
        }
    }

    /**
     * Get all table names from the database.
     */
    public static List<String> getTableNames(Connection conn) {
        List<String> tables = new ArrayList<>();
        try {
            ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                // Skip H2 internal tables
                if (!tableName.startsWith("INFORMATION_SCHEMA")) {
                    tables.add(tableName);
                }
            }
            rs.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get table names", e);
        }
        return tables;
    }

    /**
     * Count the number of rows in a table.
     */
    public static int countRows(Database db, String tableName) {
        try (Connection conn = db.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName);
            if (rs.next()) {
                int count = rs.getInt(1);
                rs.close();
                stmt.close();
                return count;
            }
            rs.close();
            stmt.close();
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to count rows in table: " + tableName, e);
        }
    }

    /**
     * Check if a table exists in the database.
     */
    public static boolean tableExists(Database db, String tableName) {
        try (Connection conn = db.getConnection()) {
            ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"});
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check if table exists: " + tableName, e);
        }
    }
}
