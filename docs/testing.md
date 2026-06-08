# VInject Testing Guide

Testing is a core part of working with VInject. This guide covers how to write both isolated unit tests and full-context integration tests with the framework.

---

## 1. Unit Testing with Mockito

Because VInject components use standard dependency injection, you can easily mock their dependencies using Mockito. This runs quickly and does not load the VInject container.

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void testRegisterUser() {
        // Arrange
        User mockUser = new User();
        mockUser.setUsername("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(mockUser);

        // Act
        User result = userService.getUserByUsername("testuser");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
    }
}
```

---

## 2. Integration Testing (`TestApplicationContext`)

For integration testing where you want to verify that multiple components load and interact correctly within the container, use `TestApplicationContext` in a try-with-resources statement.

```java
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ComponentIntegrationTest {

    @Test
    void testComponentInteraction() {
        // Build an isolated context with specific classes
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(ServiceA.class, ServiceB.class)
                .build()) {

            // Get instances directly from the test context
            ServiceA serviceA = context.getComponent(ServiceA.class);
            String result = serviceA.doWork();

            // Assert
            assertThat(result).isEqualTo("success");
        }
    }
}
```

---

## 3. Database & Repository Testing (`MockDatabaseBuilder`)

To test `@Repository` instances, spin up a lightweight, in-memory H2 database using `MockDatabaseBuilder` and register it into your repository context using `RepositoryTestUtils`.

```java
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.RepositoryTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest {

    private Database database;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 1. Create a fresh in-memory H2 database
        database = MockDatabaseBuilder.createInMemory("test_db");

        // 2. Generate proxy instance for the repository interface
        userRepository = RepositoryTestUtils.createRepository(UserRepository.class, database);
    }

    @AfterEach
    void tearDown() {
        // 3. Clear database tables after each test to ensure isolation
        RepositoryTestUtils.clearDatabase(database);
    }

    @Test
    void saveAndFindUser() {
        User user = new User();
        UUID id = UUID.randomUUID();
        user.setId(id);
        user.setUsername("alice");
        user.setCoins(100);

        userRepository.save(user);

        User found = userRepository.findById(id);
        assertThat(found).isNotNull();
        assertThat(found.getUsername()).isEqualTo("alice");
    }
}
```

---

## 4. Best Practices & Anti-Patterns

### Anti-Pattern: Shared Databases
Do not reuse a static database connection across tests. This will lead to tests corrupting each other's data:
```java
private static Database db = MockDatabaseBuilder.createInMemory(); // Bad!
```

### Best Practice: Fresh Database
Create a new database instance for each test run inside `@BeforeEach` and close it in `@AfterEach`:
```java
@BeforeEach
void setUp() {
    database = MockDatabaseBuilder.createInMemory(); // Good!
}
```
