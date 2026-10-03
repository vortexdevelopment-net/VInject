package net.vortexdevelopment.vinject.integration;

import lombok.Data;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.Value;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnDestroy;
import net.vortexdevelopment.vinject.annotation.lifecycle.PostConstruct;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that verify multiple components working together.
 */
class FullApplicationIntegrationTest {

    private TestApplicationContext context;
    private Database database;

    @BeforeEach
    void setUp() {
        database = MockDatabaseBuilder.createInMemory("integration_test");
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void fullApplicationStartupWithAllFeatures() {
        // Arrange & Act
        context = TestApplicationContext.builder()
                .withRootClass(FullTestRoot.class)
                .withDatabase(database)
                .build();

        // Get service that uses multiple features
        UserService service = context.getComponent(UserService.class);

        // Assert: Verify all dependencies are wired correctly
        assertThat(service).isNotNull();
        assertThat(service.userRepository).isNotNull();
        assertThat(service.emailService).isNotNull();
        assertThat(service.isInitialized()).isTrue();

        // Test full workflow: create user, save, find
        IntegrationUser user = service.createUser("John Doe", "john@example.com");
        IntegrationUser found = service.findUser(user.getId());

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("John Doe");
        assertThat(found.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void componentsWithTransitiveDependenciesWork() {
        // Arrange & Act
        context = TestApplicationContext.builder()
                .withRootClass(FullTestRoot.class)
                .withDatabase(database)
                .build();

        // ServiceA -> ServiceB -> ServiceC chain
        ServiceA serviceA = context.getComponent(ServiceA.class);

        // Assert: Full chain should be wired
        assertThat(serviceA.serviceB).isNotNull();
        assertThat(serviceA.serviceB.serviceC).isNotNull();
        assertThat(serviceA.performOperation()).isEqualTo("A->B->C");
    }

    @Test
    void lifecycleMethodsWorkWithDependencies() {
        // Arrange & Act
        context = TestApplicationContext.builder()
                .withRootClass(FullTestRoot.class)
                .withDatabase(database)
                .build();

        LifecycleService service = context.getComponent(LifecycleService.class);

        // Assert: PostConstruct should have run with dependencies available
        assertThat(service.getInitMessage()).contains("initialized");
        assertThat(service.hasDependency()).isTrue();

        // Close context to trigger OnDestroy
        context.close();
        context = null;

        assertThat(service.isCleanedUp()).isTrue();
    }

    @Test
    void valueAnnotationWorksWithComponents() {
        // Arrange
        System.setProperty("app.name", "Integration Test App");

        try {
            // Act
            context = TestApplicationContext.builder()
                    .withRootClass(FullTestRoot.class)
                    .withDatabase(database)
                    .build();

            ConfigurableService service = context.getComponent(ConfigurableService.class);

            // Assert
            assertThat(service.getAppName()).isEqualTo("Integration Test App");
        } finally {
            System.clearProperty("app.name");
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.integration", createInstance = false)
    static class FullTestRoot {
    }

    @Entity(table = "INTEGRATION_USERS")
    @Data
    public static class IntegrationUser {
        @Id
        private UUID id;

        @Column
        private String name;

        @Column
        private String email;
    }

    @Repository
    public interface IntegrationUserRepository extends CrudRepository<IntegrationUser, UUID> {
        IntegrationUser findByEmail(String email);
    }

    @Component
    public static class UserService {
        @Inject
        public IntegrationUserRepository userRepository;

        @Inject
        public EmailService emailService;

        private boolean initialized = false;

        @PostConstruct
        public void init() {
            initialized = true;
        }

        public IntegrationUser createUser(String name, String email) {
            IntegrationUser user = new IntegrationUser();
            user.setId(UUID.randomUUID());
            user.setName(name);
            user.setEmail(email);
            userRepository.save(user);
            emailService.sendWelcomeEmail(email);
            return user;
        }

        public IntegrationUser findUser(UUID id) {
            return userRepository.findById(id);
        }

        public boolean isInitialized() {
            return initialized;
        }
    }

    @Component
    public static class EmailService {
        public void sendWelcomeEmail(String email) {
            // Mock email sending
        }
    }

    @Component
    public static class ServiceA {
        @Inject
        public ServiceB serviceB;

        public String performOperation() {
            return "A->" + serviceB.performOperation();
        }
    }

    @Component
    public static class ServiceB {
        @Inject
        public ServiceC serviceC;

        public String performOperation() {
            return "B->" + serviceC.performOperation();
        }
    }

    @Component
    public static class ServiceC {
        public String performOperation() {
            return "C";
        }
    }

    @Component
    public static class LifecycleService {
        @Inject
        public EmailService emailService;

        private String initMessage;
        private boolean cleanedUp = false;

        @PostConstruct
        public void init() {
            if (emailService != null) {
                initMessage = "Service initialized with dependencies";
            } else {
                initMessage = "Service initialized without dependencies";
            }
        }

        @OnDestroy
        public void cleanup() {
            cleanedUp = true;
        }

        public String getInitMessage() {
            return initMessage;
        }

        public boolean hasDependency() {
            return emailService != null;
        }

        public boolean isCleanedUp() {
            return cleanedUp;
        }
    }

    @Component
    public static class ConfigurableService {
        @Value("${app.name:Default App}")
        private String appName;

        public String getAppName() {
            return appName;
        }
    }
}
