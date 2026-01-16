package net.vortexdevelopment.vinject.testing;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.RepositoryContainer;
import net.vortexdevelopment.vinject.di.DependencyContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-friendly application context that provides programmatic control over component initialization
 * without requiring VInjectApplication.run(). This enables isolated subsystem testing and mocking.
 * 
 * <p>Usage example:
 * <pre>
 * {@code
 * try (TestApplicationContext context = TestApplicationContext.builder()
 *         .withComponents(ComponentA.class, ComponentB.class)
 *         .withMockDatabase()
 *         .build()) {
 *     
 *     ComponentA compA = context.getComponent(ComponentA.class);
 *     // Test component behavior
 * }
 * }
 * </pre>
 */
public class TestApplicationContext implements AutoCloseable {

    private DependencyContainer container;
    private Database database;
    private RepositoryContainer repositoryContainer;
    private final Map<Class<?>, Object> mockInstances = new HashMap<>();
    private final List<Class<?>> componentClasses = new ArrayList<>();
    private final Class<?> rootClass;
    private final Root rootAnnotation;
    private boolean initialized = false;

    private TestApplicationContext(Builder builder) {
        this.rootClass = builder.rootClass;
        this.componentClasses.addAll(builder.componentClasses);
        this.mockInstances.putAll(builder.mockInstances);
        this.database = builder.database;

        // Create a minimal Root annotation if not present
        if (rootClass.isAnnotationPresent(Root.class)) {
            this.rootAnnotation = rootClass.getAnnotation(Root.class);
        } else {
            // Create synthetic Root annotation
            this.rootAnnotation = createSyntheticRootAnnotation(builder.packageName);
        }
    }

    /**
     * Create a builder for configuring the test application context.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Initialize all components and perform dependency injection.
     * This method must be called before getting components.
     */
    public TestApplicationContext initialize() {
        if (initialized) {
            throw new IllegalStateException("Context already initialized");
        }

        // Initialize database if needed
        if (database != null) {
            database.init();
            repositoryContainer = new RepositoryContainer(database);
        }

        // Create dependency container
        Object rootInstance = null;
        if (rootAnnotation.createInstance()) {
            try {
                rootInstance = rootClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create root instance", e);
            }
        }

        container = new DependencyContainer(
                rootAnnotation,
                rootClass,
                rootInstance,
                database,
                repositoryContainer,
                (unused) -> {
                    // Register mock instances BEFORE component scanning
                    // This ensures mocks are available when components are created
                    for (Map.Entry<Class<?>, Object> entry : mockInstances.entrySet()) {
                        DependencyContainer.getInstance().addBean(entry.getKey(), entry.getValue());
                    }
                }
        );

        // Inject dependencies into root instance if it exists
        if (rootInstance != null) {
            container.getInjectionEngine().inject(rootInstance);
            container.getLifecycleManager().invokePostConstruct(rootInstance);
        }

        initialized = true;
        return this;
    }

    /**
     * Register a component class to be loaded in the context.
     */
    public TestApplicationContext registerComponent(Class<?> componentClass) {
        if (initialized) {
            throw new IllegalStateException("Cannot register components after initialization");
        }
        componentClasses.add(componentClass);
        return this;
    }

    /**
     * Register a mock instance for a specific type.
     * This instance will be injected instead of creating a new one.
     */
    public <T> TestApplicationContext registerMock(Class<T> type, T mockInstance) {
        if (initialized) {
            throw new IllegalStateException("Cannot register mocks after initialization");
        }
        mockInstances.put(type, mockInstance);
        return this;
    }

    /**
     * Get a component from the context with dependency injection.
     */
    public <T> T getComponent(Class<T> type) {
        if (!initialized) {
            throw new IllegalStateException("Context not initialized. Call initialize() first.");
        }
        return container.getDependency(type);
    }

    /**
     * Get a component from the context, or null if not found.
     */
    public <T> T getComponentOrNull(Class<T> type) {
        if (!initialized) {
            throw new IllegalStateException("Context not initialized. Call initialize() first.");
        }
        return container.getDependencyOrNull(type);
    }

    /**
     * Get the dependency container instance.
     */
    public DependencyContainer getContainer() {
        return container;
    }

    /**
     * Get the database instance.
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Get the repository container instance.
     */
    public RepositoryContainer getRepositoryContainer() {
        return repositoryContainer;
    }

    /**
     * Destroy all components and clean up resources.
     */
    public void destroy() {
        if (container != null) {
            container.getLifecycleManager().invokeDestroyMethods();
            container.release();
        }
    }

    @Override
    public void close() {
        destroy();
    }

    /**
     * Create a synthetic Root annotation with the given package name.
     */
    private Root createSyntheticRootAnnotation(String packageName) {
        return new Root() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Root.class;
            }

            @Override
            public String packageName() {
                return packageName;
            }

            @Override
            public boolean createInstance() {
                return false;
            }

            @Override
            public boolean loadProperties() {
                return false;
            }

            @Override
            public String[] ignoredPackages() {
                return new String[0];
            }

            @Override
            public String[] includedPackages() {
                return new String[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation>[] componentAnnotations() {
                return new Class[0];
            }

            @Override
            public net.vortexdevelopment.vinject.annotation.template.TemplateDependency[] templateDependencies() {
                return new net.vortexdevelopment.vinject.annotation.template.TemplateDependency[0];
            }
        };
    }

    /**
     * Builder for creating TestApplicationContext instances.
     */
    public static class Builder {
        private Class<?> rootClass = TestRootClass.class;
        private String packageName = "net.vortexdevelopment.vinject.testing";
        private final List<Class<?>> componentClasses = new ArrayList<>();
        private final Map<Class<?>, Object> mockInstances = new HashMap<>();
        private Database database;

        /**
         * Set the root class for the context.
         */
        public Builder withRootClass(Class<?> rootClass) {
            this.rootClass = rootClass;
            return this;
        }

        /**
         * Set the package name to scan for components.
         */
        public Builder withPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        /**
         * Add component classes to be loaded.
         */
        public Builder withComponents(Class<?>... componentClasses) {
            for (Class<?> clazz : componentClasses) {
                this.componentClasses.add(clazz);
            }
            return this;
        }

        /**
         * Register a mock instance for a specific type.
         */
        public <T> Builder withMock(Class<T> type, T mockInstance) {
            this.mockInstances.put(type, mockInstance);
            return this;
        }

        /**
         * Use an existing database instance.
         */
        public Builder withDatabase(Database database) {
            this.database = database;
            return this;
        }

        /**
         * Create an in-memory H2 database for testing.
         */
        public Builder withMockDatabase() {
            this.database = MockDatabaseBuilder.createInMemory();
            return this;
        }

        /**
         * Create an in-memory H2 database with specific entity classes.
         */
        public Builder withMockDatabase(Class<?>... entityClasses) {
            this.database = MockDatabaseBuilder.createWithEntities(entityClasses);
            return this;
        }

        /**
         * Build and initialize the context.
         */
        public TestApplicationContext build() {
            TestApplicationContext context = new TestApplicationContext(this);
            return context.initialize();
        }

        /**
         * Build the context without initializing.
         * You must call initialize() manually.
         */
        public TestApplicationContext buildWithoutInit() {
            return new TestApplicationContext(this);
        }
    }

    /**
     * Default test root class used when no specific root class is provided.
     */
    @Root(packageName = "net.vortexdevelopment.vinject.testing", createInstance = false)
    private static class TestRootClass {
    }
}
