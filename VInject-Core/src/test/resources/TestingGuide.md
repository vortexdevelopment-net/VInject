# VInject Testing Guide

Comprehensive guide for writing tests with the VInject framework, including unit testing with mocks and integration testing strategies.

## Table of Contents

1. [Unit Testing with Mocks](#unit-testing-with-mocks)
2. [Integration Testing](#integration-testing)
3. [Repository Testing](#repository-testing)
4. [Configuration Testing](#configuration-testing)
5. [Common Patterns](#common-patterns)

---

## Unit Testing with Mocks

Unit tests focus on testing a single component in isolation, using mocks for dependencies.

### Example: Testing a Component with Mockito

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyComponentTest {
    
    @Mock
    private DependencyA depA;
    
    @Mock
    private DependencyB depB;
    
    @InjectMocks
    private MyComponent component;
    
    @Test
    void testProcessingLogic() {
        // Arrange
        when(depA.getData()).thenReturn("test-data");
        when(depB.transform("test-data")).thenReturn("transformed");
        
        // Act
        String result = component.process();
        
        // Assert
        assertThat(result).isEqualTo("transformed");
    }
}
```

### Key Points:
- Use `@ExtendWith(MockitoExtension.class)` to enable Mockito
- Use `@Mock` to create mock instances
- Use `@InjectMocks` to inject mocks into the class under test
- Use AssertJ's fluent assertions for readability

---

## Integration Testing

Integration tests verify that multiple components work together correctly.

### Example: Testing Component Interaction

```java
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentInteractionTest {
    
    @Test
    void componentsInteractCorrectly() {
        // Arrange: Create context with real components
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(ServiceA.class, ServiceB.class)
                .build()) {
            
            // Act: Get components and test interaction
            ServiceA serviceA = context.getComponent(ServiceA.class);
            String result = serviceA.performOperation();
            
            // Assert
            assertThat(result).isNotNull();
        }
    }
}
```

### Key Points:
- Use `TestApplicationContext` for controlled component loading
- Use try-with-resources to ensure proper cleanup
- Test real interactions without mocks when possible

---

## Repository Testing

Repository tests verify database operations and query generation.

### Example: Testing Repository with H2 Database

```java
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.RepositoryTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest {
    
    private Database database;
    private UserRepository userRepository;
    
    @BeforeEach
    void setUp() {
        // Create in-memory H2 database
        database = MockDatabaseBuilder.createInMemory("test_users");
        
        // Create repository
        userRepository = RepositoryTestUtils.createRepository(
            UserRepository.class, 
            database
        );
    }
    
    @AfterEach
    void tearDown() {
        // Clean up test data
        RepositoryTestUtils.clearDatabase(database);
    }
    
    @Test
    void saveAndRetrieveUser() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setName("John Doe");
        user.setEmail("john@example.com");
        
        // Act
        userRepository.save(user);
        User found = userRepository.findById(1L);
        
        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("John Doe");
        assertThat(found.getEmail()).isEqualTo("john@example.com");
    }
    
    @Test
    void findByNameGeneratesCorrectQuery() {
        // Arrange
        User user = new User();
        user.setId(1L);
        user.setName("Alice");
        userRepository.save(user);
        
        // Act
        User found = userRepository.findByName("Alice");
        
        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Alice");
    }
}
```

### Key Points:
- Use `MockDatabaseBuilder` for H2 database creation
- Clean up test data in `@AfterEach` to ensure test isolation
- Use `RepositoryTestUtils` for common database operations

---

## Configuration Testing

Test YAML configuration loading and injection.

### Example: Testing Configuration Loading

```java
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoadingTest {
    
    @TempDir
    File tempDir;
    
    @Test
    void configurationLoadsCorrectly() throws Exception {
        // Arrange: Create test YAML file
        File configFile = new File(tempDir, "app-config.yml");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("app-name: Test App\n");
            writer.write("port: 8080\n");
        }
        
        // Act: Load configuration (implementation depends on your setup)
        // ...
        
        // Assert
        // Verify configuration values are loaded correctly
    }
}
```

---

## Common Patterns

### Pattern 1: Testing with Partial Mocks

Mix real and mock dependencies:

```java
try (TestApplicationContext context = TestApplicationContext.builder()
        .withRootClass(TestRoot.class)
        .withMock(ExternalService.class, mockExternalService)
        .build()) {
    
    // Real component with mocked external dependency
    MyService service = context.getComponent(MyService.class);
    service.performOperation();
    
    verify(mockExternalService).externalCall();
}
```

### Pattern 2: Testing Lifecycle Methods

```java
@Test
void lifecycleMethodsCalledInOrder() {
    try (TestApplicationContext context = TestApplicationContext.builder()
            .withRootClass(TestRoot.class)
            .build()) {
        
        LifecycleComponent comp = context.getComponent(LifecycleComponent.class);
        
        // Verify @PostConstruct was called
        assertThat(comp.isInitialized()).isTrue();
        
        // Destroy context
        context.destroy();
        
        // Verify @OnDestroy was called
        assertThat(comp.isDestroyed()).isTrue();
    }
}
```

### Pattern 3: Testing Component Dependencies

```java
import net.vortexdevelopment.vinject.testing.ComponentTestUtils;

@Test
void allDependenciesInjected() {
    try (TestApplicationContext context = TestApplicationContext.builder()
            .withRootClass(TestRoot.class)
            .build()) {
        
        MyComponent comp = context.getComponent(MyComponent.class);
        
        // Verify all @Inject fields are not null
        assertThat(ComponentTestUtils.verifyAllDependenciesInjected(comp))
            .isTrue();
    }
}
```

### Anti-Patterns to Avoid

**❌ Don't: Create components manually without dependency injection**
```java
// Bad: Manual instantiation loses DI benefits
MyComponent comp = new MyComponent();
comp.dependency = new Dependency();
```

**✅ Do: Use TestApplicationContext**
```java
// Good: Let the framework handle DI
try (TestApplicationContext context = ...) {
    MyComponent comp = context.getComponent(MyComponent.class);
    // All dependencies are injected
}
```

**❌ Don't: Share database instances between tests**
```java
// Bad: Tests interfere with each other
private static Database sharedDb = MockDatabaseBuilder.createInMemory();
```

**✅ Do: Create fresh database for each test**
```java
// Good: Each test is isolated
@BeforeEach
void setUp() {
    database = MockDatabaseBuilder.createInMemory();
}
```

---

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ComponentLoadingOrderTest

# Run with coverage
mvn test jacoco:report
```
