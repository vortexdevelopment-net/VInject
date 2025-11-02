package net.vortexdevelopment.vinject;

import net.vortexdevelopment.vinject.annotation.Root;
import net.vortexdevelopment.vinject.config.Environment;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import org.jetbrains.annotations.Nullable;

import net.vortexdevelopment.vinject.annotation.database.Entity;
import org.reflections.Configuration;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Main application class for VInject framework, similar to Spring Boot's SpringApplication.
 * Handles application initialization, lifecycle management, and graceful shutdown.
 * 
 * <p>Usage example:
 * <pre>
 * {@code
 * @Root(packageName = "com.example.app")
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         VInjectApplication.run(MyApplication.class, args);
 *     }
 * }
 * }
 * </pre>
 */
public class VInjectApplication {

    private static volatile boolean running = false;
    private static Thread mainThread;
    private static DependencyContainer dependencyContainer;
    private static Database database;
    private static RepositoryContainer repositoryContainer;
    private static Thread shutdownHookThread;
    private static Object rootInstance;
    private static Class<?> rootClass;
    private static final Object shutdownLock = new Object();
    private static CountDownLatch shutdownHookComplete;

    /**
     * Run the VInject application with the specified root class.
     * This method will initialize the dependency container, start a thread to keep the app running,
     * and register shutdown hooks for graceful shutdown.
     * 
     * <p>Database configuration will be read from application.properties if a Database bean is provided.
     * If entity classes are present and no Database bean is configured, the application will fail to start.
     *
     * @param rootClass The main class annotated with @Root
     * @param args Command line arguments
     * @return The dependency container instance
     */
    public static DependencyContainer run(Class<?> rootClass, String... args) {
        return run(rootClass, (Database) null, args);
    }

    /**
     * Run the VInject application with database configuration.
     *
     * @param rootClass The main class annotated with @Root
     * @param dbHost Database host
     * @param dbPort Database port
     * @param dbName Database name
     * @param dbType Database type (mysql, mariadb, h2)
     * @param dbUsername Database username
     * @param dbPassword Database password
     * @param maxPoolSize Maximum connection pool size
     * @param h2File H2 database file (only needed for h2 type)
     * @param args Command line arguments
     * @return The dependency container instance
     */
    public static DependencyContainer run(Class<?> rootClass, 
                                          String dbHost, String dbPort, String dbName,
                                          String dbType, String dbUsername, String dbPassword,
                                          int maxPoolSize, @Nullable File h2File,
                                          String... args) {

        Root rootAnnotation = rootClass.getAnnotation(Root.class);

        // Validate root class annotation
        if (!rootClass.isAnnotationPresent(Root.class)) {
            throw new RuntimeException("Class " + rootClass.getName() + " must be annotated with @Root");
        }

        if (rootAnnotation.loadProperties()) {
            // Initialize Environment FIRST - load application.properties and set system properties
            // This must happen before dependency injection so @Value annotations can resolve properties
            Environment.initialize();
        }

        // Initialize database if configuration provided
        if (dbHost != null && dbType != null) {
            database = new Database(dbHost, dbPort, dbName, dbType, dbUsername, dbPassword, maxPoolSize, 
                    h2File != null ? h2File : new File("./h2Data"));
            database.init();
            repositoryContainer = new RepositoryContainer(database);
        } else {
            // Create a minimal database instance for compatibility
            // This may not work for all cases, but allows apps without database
            database = null;
            repositoryContainer = null;
        }

        // Initialize dependency container
        Object rootInstance = null;
        if (rootAnnotation.createInstance()) {
            try {
                rootInstance = rootClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create root instance", e);
            }
        }

        // Store root instance and class for later use
        VInjectApplication.rootInstance = rootInstance;
        VInjectApplication.rootClass = rootClass;

        dependencyContainer = new DependencyContainer(
                rootAnnotation,
                rootClass,
                rootInstance,
                database,
                repositoryContainer,
                null
        );

        // Inject dependencies into root instance if it exists
        if (rootInstance != null) {
            dependencyContainer.inject(rootInstance);
        }

        // Register a shutdown hook BEFORE starting the main thread
        registerShutdownHook();

        // Start the main thread to keep the application running
        running = true;
        mainThread = Thread.currentThread();
        startMainThread();

        return dependencyContainer;
    }

    /**
     * Run the VInject application with a pre-configured Database instance.
     *
     * @param rootClass The main class annotated with @Root
     * @param database Pre-configured Database instance (can be null)
     * @param args Command line arguments
     * @return The dependency container instance
     */
    public static DependencyContainer run(Class<?> rootClass, @Nullable Database database, String... args) {
        try {
            // Initialize Environment FIRST - load application.properties and set system properties
            // This must happen before dependency injection so @Value annotations can resolve properties
            Environment.initialize();
            
            // Validate root class annotation
            if (!rootClass.isAnnotationPresent(Root.class)) {
                throw new RuntimeException("Class " + rootClass.getName() + " must be annotated with @Root");
            }

            Root rootAnnotation = rootClass.getAnnotation(Root.class);

            // Pre-scan for entities to determine if Database is required
            boolean hasEntities = scanForEntities(rootAnnotation, rootClass);
            
            // Try to load database configuration from application.properties if database bean might be provided
            Database databaseFromProps = loadDatabaseFromProperties();
            if (databaseFromProps != null && database == null) {
                database = databaseFromProps;
            }

            // Validate: If entities exist, Database must be configured BEFORE container creation
            if (hasEntities && database == null) {
                throw new RuntimeException(
                        "Entity classes are present, but no Database configuration was provided. " +
                        "Please either:\n" +
                        "1. Provide a Database bean via @Bean method (configuration will be read from application.properties), or\n" +
                        "2. Configure database in application.properties, or\n" +
                        "3. Pass a Database instance to VInjectApplication.run()"
                );
            }

            // Use provided database or create repository container
            VInjectApplication.database = database;
            if (database != null) {
                repositoryContainer = new RepositoryContainer(database);
            } else {
                repositoryContainer = null;
            }

            // Initialize dependency container
            Object rootInstance = null;
            if (rootAnnotation.createInstance()) {
                try {
                    rootInstance = rootClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Unable to create root instance", e);
                }
            }

            // Store root instance and class for later use
            VInjectApplication.rootInstance = rootInstance;
            VInjectApplication.rootClass = rootClass;

            dependencyContainer = new DependencyContainer(
                    rootAnnotation,
                    rootClass,
                    rootInstance,
                    database,
                    repositoryContainer,
                    null
            );

            // Register shutdown hook EARLY - BEFORE @PostConstruct, in case @PostConstruct calls shutdown()
            registerShutdownHook();

            // Inject dependencies into root instance if it exists
            if (rootInstance != null) {
                dependencyContainer.inject(rootInstance);
                // Invoke @PostConstruct on root instance after injection
                dependencyContainer.invokePostConstruct(rootInstance);
            }

            // Check if Database bean exists in the container after component loading
            Database databaseBean = getDatabaseBeanFromContainer();
            if (databaseBean != null && database == null) {
                // Database bean was provided via @Bean - use it
                database = databaseBean;
                VInjectApplication.database = database;
                if (repositoryContainer == null) {
                    repositoryContainer = new RepositoryContainer(database);
                }
            }

            // Start the main thread to keep the application running
            running = true;
            mainThread = Thread.currentThread();
            startMainThread();

            return dependencyContainer;
        } catch (RuntimeException e) {
            // Critical error during startup - perform error shutdown
            handleStartupError(e);
            throw e; // Re-throw after shutdown
        } catch (Exception e) {
            // Unexpected error during startup - perform error shutdown
            handleStartupError(new RuntimeException("Unexpected error during application startup", e));
            throw new RuntimeException("Unexpected error during application startup", e);
        }
    }

    /**
     * Load database configuration from application.properties file.
     * The properties file should be located in the classpath root.
     *
     * @return Database instance if configuration is found, null otherwise
     */
    private static Database loadDatabaseFromProperties() {
        Properties props = new Properties();
        try (InputStream inputStream = VInjectApplication.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (inputStream == null) {
                // application.properties not found, that's okay
                return null;
            }
            props.load(inputStream);

            // Check if database configuration exists
            String dbHost = props.getProperty("database.host");
            String dbType = props.getProperty("database.type");

            // If no database configuration, return null
            if (dbHost == null && dbType == null) {
                return null;
            }

            // If only partial configuration, that's an error
            if (dbHost == null || dbType == null) {
                throw new RuntimeException("Incomplete database configuration in application.properties. " +
                        "Both database.host and database.type are required.");
            }

            String dbPort = props.getProperty("database.port", "3306");
            String dbName = props.getProperty("database.name", "vinject_app");
            String dbUsername = props.getProperty("database.username", "root");
            String dbPassword = props.getProperty("database.password", "");
            int maxPoolSize = Integer.parseInt(props.getProperty("database.pool-size", "10"));
            String h2FilePath = props.getProperty("database.h2.file", "./h2Data");

            File h2File = new File(h2FilePath);
            Database database = new Database(dbHost, dbPort, dbName, dbType, dbUsername, dbPassword, maxPoolSize, h2File);
            database.init();
            return database;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load database configuration from application.properties", e);
        }
    }

    /**
     * Check if a Database bean exists in the dependency container.
     *
     * @return Database instance if found, null otherwise
     */
    private static Database getDatabaseBeanFromContainer() {
        if (dependencyContainer == null) {
            return null;
        }

        try {
            // Try to get Database from the container
            return dependencyContainer.getDependencyOrNull(Database.class);
        } catch (Exception e) {
            // Database not found in container
            return null;
        }
    }

    /**
     * Get the effective package name from the root annotation or detect it from the root class.
     * If packageName is specified in the annotation (non-empty), it will be used.
     * Otherwise, the package name will be detected from the root class.
     *
     * @param rootAnnotation The @Root annotation
     * @param rootClass The root class
     * @return The effective package name to scan
     */
    private static String getEffectivePackageName(Root rootAnnotation, Class<?> rootClass) {
        String packageName = rootAnnotation.packageName();
        if (packageName == null || packageName.isEmpty()) {
            // Auto-detect package name from root class
            Package pkg = rootClass.getPackage();
            if (pkg != null) {
                packageName = pkg.getName();
            } else {
                // Fallback: extract package name from class name
                String className = rootClass.getName();
                int lastDot = className.lastIndexOf('.');
                if (lastDot > 0) {
                    packageName = className.substring(0, lastDot);
                } else {
                    // Default package - use empty string
                    packageName = "";
                }
            }
        }
        return packageName;
    }

    /**
     * Pre-scan for entity classes to determine if Database configuration is required.
     * This is done before DependencyContainer creation to validate Database configuration.
     *
     * @param rootAnnotation The @Root annotation containing package scanning configuration
     * @param rootClass The root class
     * @return true if entities are found, false otherwise
     */
    private static boolean scanForEntities(Root rootAnnotation, Class<?> rootClass) {
        String[] ignoredPackages = rootAnnotation.ignoredPackages();
        String[] includedPackages = rootAnnotation.includedPackages();
        String rootPackage = getEffectivePackageName(rootAnnotation, rootClass);
        // Convert package name to path format (e.g., "net.vortexdevelopment.vinject.app" -> "net/vortexdevelopment/vinject/app")
        String rootPackagePath = rootPackage.replace('.', '/');

        Configuration configuration = new ConfigurationBuilder()
                .forPackage(rootPackage)
                .filterInputsBy(s -> {
                    if (s == null) return false;
                    if (s.startsWith("META-INF")) return false;
                    if (!s.endsWith(".class")) return false;

                    // First check: Only include classes that are under the root package
                    if (!s.startsWith(rootPackagePath + "/") && !s.equals(rootPackagePath + ".class")) {
                        return false;
                    }

                    // Check ignored packages (must match the path format)
                    for (String ignoredPackage : ignoredPackages) {
                        String ignoredPath = ignoredPackage.replace('.', '/');
                        if (s.startsWith(ignoredPath)) {
                            return false;
                        }
                    }

                    // Check included packages (override ignored packages)
                    for (String includedPackage : includedPackages) {
                        String includedPath = includedPackage.replace('.', '/');
                        if (s.startsWith(includedPath)) {
                            return true;
                        }
                    }

                    // Default: include if under root package (already checked above)
                    return true;
                });

        Reflections reflections = new Reflections(configuration);
        Set<Class<?>> entities = reflections.getTypesAnnotatedWith(Entity.class);
        return !entities.isEmpty();
    }


    /**
     * Register a shutdown hook that will be called when the JVM receives an interrupt signal.
     * This hook will run for System.exit() calls and other termination scenarios.
     * For SIGINT, we handle it directly in the interrupt handler.
     */
    private static void registerShutdownHook() {
        // Initialize the latch before creating the hook thread
        shutdownHookComplete = new CountDownLatch(1);
        
        shutdownHookThread = new Thread(() -> {
            try {
                // Only perform shutdown if not already done
                // This handles cases like System.exit() or other termination scenarios
                if (!shutdownPerformed) {
                    performShutdown();
                }
            } catch (Exception e) {
                System.err.println("Error in shutdown hook: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Ensure the hook thread completes before JVM exits
                System.out.flush();
                System.err.flush();
                // Signal that the shutdown hook has completed
                shutdownHookComplete.countDown();
            }
        });
        shutdownHookThread.setDaemon(false); // Non-daemon thread so it runs during shutdown
        shutdownHookThread.setName("VInject-ShutdownHook");
        shutdownHookThread.setPriority(Thread.MAX_PRIORITY); // High priority to ensure it runs
        
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        } catch (IllegalStateException e) {
            System.err.println("Warning: Cannot register shutdown hook - JVM is already shutting down");
        } catch (Exception e) {
            System.err.println("Error registering shutdown hook: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static volatile boolean shutdownPerformed = false;

    /**
     * Handle critical errors during application startup by performing error shutdown.
     * This ensures the application exits cleanly even when startup fails.
     * 
     * @param error The error that occurred during startup
     */
    private static void handleStartupError(Throwable error) {
        System.err.println("CRITICAL ERROR during application startup:");
        System.err.println(error.getMessage());
        error.printStackTrace();
        System.err.flush();
        
        // Try to perform cleanup if possible
        try {
            // Ensure shutdown hook is registered before attempting cleanup
            if (shutdownHookThread == null) {
                registerShutdownHook();
            }
            
            // Perform shutdown cleanup if container was initialized
            if (dependencyContainer != null && !shutdownPerformed) {
                performShutdown();
            }
        } catch (Exception e) {
            System.err.println("Error during error shutdown cleanup: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always exit with error code
            System.exit(1);
        }
    }

    /**
     * Perform the actual shutdown cleanup: invoke @OnDestroy methods, release resources, etc.
     * This method is called both by the shutdown hook and by the shutdown() method.
     * Uses a volatile flag to ensure cleanup only happens once, even if called from multiple threads.
     */
    private static synchronized void performShutdown() {
        // Use synchronized to ensure cleanup only happens once
        if (shutdownPerformed) {
            return; // Already performed shutdown
        }
        shutdownPerformed = true;
        
        // Set running to false and notify waiting threads
        synchronized (shutdownLock) {
            running = false;
            shutdownLock.notifyAll(); // Wake up the main thread if it's waiting
        }

        System.out.println("Shutting down VInject application...");

        // Call all @OnDestroy methods
        if (dependencyContainer != null) {
            dependencyContainer.invokeDestroyMethods();
        }

        // Release dependency container resources
        if (dependencyContainer != null) {
            dependencyContainer.release();
        }

        // Close database connection if present
        if (database != null) {
            try {
                // Database might have a close method or similar
                // For now, we'll just log
                System.out.println("Closing database connections...");
            } catch (Exception e) {
                System.err.println("Error closing database: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("VInject application shut down complete.");
        // Flush output to ensure messages are printed before JVM exits
        System.out.flush();
        System.err.flush();
    }


    /**
     * Start a thread that keeps the application running until interrupted.
     */
    private static void startMainThread() {
        // Keep the main thread alive using wait() instead of polling
        try {
            synchronized (shutdownLock) {
                while (running) {
                    shutdownLock.wait(); // Wait until notified or interrupted
                }
            }
            // Loop exited because running became false
            System.out.println("Application shutdown requested, exiting...");
        } catch (InterruptedException e) {
            // SIGINT (Ctrl+C) interrupts the wait
            // The JVM will trigger shutdown hooks automatically when SIGINT is received
            // We need to perform shutdown and exit gracefully
            
            // Set running to false
            synchronized (shutdownLock) {
                running = false;
                shutdownLock.notifyAll(); // Wake up any waiting threads
            }
            
            // Perform shutdown immediately in the main thread
            // The shutdown hook may also run, but performShutdown() uses a flag to prevent duplicates
            if (!shutdownPerformed) {
                performShutdown();
            }
            
            // Wait for shutdown hook to complete if it's running
            // The shutdown hook runs in parallel with JVM shutdown
            if (shutdownHookComplete != null) {
                try {
                    // Read shutdown timeout from configuration (default: 5 seconds)
                    Environment env = Environment.getInstance();
                    long shutdownTimeoutSeconds = env.getPropertyAsLong("app.shutdown.timeout", 5);
                    shutdownHookComplete.await(shutdownTimeoutSeconds, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // If interrupted while waiting, continue with shutdown anyway
                }
            }
            
            // Exit gracefully with code 0
            System.exit(0);
        }
    }

    /**
     * Shutdown the application programmatically and exit the JVM.
     * This performs graceful shutdown by invoking @OnDestroy methods and releasing resources,
     * then exits the JVM with status code 0 (normal termination).
     * 
     * For shutdown without exiting the JVM, use shutdown(false).
     */
    public static void shutdown() {
        shutdown(0);
    }

    /**
     * Shutdown the application and exit the JVM with the specified status code.
     * This performs graceful shutdown by invoking @OnDestroy methods and releasing resources,
     * then exits the JVM with the specified status code.
     * 
     * @param statusCode The exit status code (0 indicates normal termination)
     */
    public static void shutdown(int statusCode) {
        // Remove the shutdown hook first to prevent it from running when we call System.exit()
        // We'll do the cleanup ourselves
        if (shutdownHookThread != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            } catch (IllegalStateException e) {
                // Shutdown hook is already running or JVM is shutting down, that's okay
                // In this case, let the shutdown hook handle it
                return;
            }
        }

        try {
            // Interrupt the main thread first (so it wakes up immediately)
            if (mainThread != null && mainThread != Thread.currentThread()) {
                mainThread.interrupt();
            }

            // Perform the actual shutdown cleanup (this sets running = false internally)
            // This will invoke all @Destroy methods
            performShutdown();
        } catch (Exception e) {
            System.err.println("Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always exit the JVM, even if shutdown cleanup failed
            System.exit(statusCode);
        }
    }

    /**
     * Shutdown the application programmatically without exiting the JVM.
     * This performs graceful shutdown by invoking @OnDestroy methods and releasing resources,
     * but does not exit the JVM. The application loop will stop, but the process will remain alive.
     * 
     * @param exit If false, shutdown without exiting JVM. If true, shutdown and exit (same as shutdown()).
     */
    public static void shutdown(boolean exit) {
        // Interrupt the main thread first (so it wakes up immediately)
        if (mainThread != null && mainThread != Thread.currentThread()) {
            mainThread.interrupt();
        }

        // Perform the actual shutdown cleanup (this sets running = false internally)
        performShutdown();

        // Exit the JVM if requested
        if (exit) {
            System.exit(0);
        }
    }

    /**
     * Check if the application is currently running.
     *
     * @return true if the application is running, false otherwise
     */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Get the dependency container instance.
     *
     * @return The dependency container, or null if not initialized
     */
    @Nullable
    public static DependencyContainer getDependencyContainer() {
        return dependencyContainer;
    }
}

