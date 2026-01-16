package net.vortexdevelopment.vinject.database.repository;

import lombok.Data;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.RepositoryTestUtils;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for repository CRUD operations using TestApplicationContext.
 */
class RepositoryCrudTest {

    private TestApplicationContext context;
    private TestUserRepository userRepository;
    private Database database;

    @BeforeEach
    void setUp() {
        database = MockDatabaseBuilder.createInMemory("crud_test");
        
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .build();
        
        userRepository = context.getComponent(TestUserRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            RepositoryTestUtils.clearDatabase(database);
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void saveEntityPersistsToDatabase() {
        // Arrange
        TestUser user = new TestUser();
        user.setId(UUID.randomUUID());
        user.setName("John Doe");
        user.setEmail("john@example.com");
        user.setAge(30);

        // Act
        userRepository.save(user);

        // Assert
        TestUser found = userRepository.findById(user.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("John Doe");
        assertThat(found.getEmail()).isEqualTo("john@example.com");
        assertThat(found.getAge()).isEqualTo(30);
    }

    @Test
    void findByIdReturnsNullWhenNotFound() {
        // Act
        TestUser found = userRepository.findById(UUID.randomUUID());

        // Assert
        assertThat(found).isNull();
    }

    @Test
    void findAllReturnsAllEntities() {
        // Arrange
        TestUser user1 = createUser("Alice", "alice@example.com", 25);
        TestUser user2 = createUser("Bob", "bob@example.com", 30);
        TestUser user3 = createUser("Charlie", "charlie@example.com", 35);

        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        // Act
        List<TestUser> all = (List<TestUser>) userRepository.findAll();

        // Assert
        assertThat(all).hasSize(3);
        assertThat(all).extracting(TestUser::getName)
                .containsExactlyInAnyOrder("Alice", "Bob", "Charlie");
    }

    @Test
    void deleteByIdRemovesEntity() {
        // Arrange
        TestUser user = createUser("ToDelete", "delete@example.com", 25);
        userRepository.save(user);

        // Act
        userRepository.deleteById(user.getId());

        // Assert
        TestUser found = userRepository.findById(user.getId());
        assertThat(found).isNull();
    }

    @Test
    void saveUpdatesExistingEntity() {
        // Arrange
        TestUser user = createUser("Original", "original@example.com", 25);
        userRepository.save(user);

        // Act: Update the entity
        user.setName("Updated");
        user.setEmail("updated@example.com");
        user.setAge(26);
        userRepository.save(user);

        // Assert
        TestUser found = userRepository.findById(user.getId());
        assertThat(found.getName()).isEqualTo("Updated");
        assertThat(found.getEmail()).isEqualTo("updated@example.com");
        assertThat(found.getAge()).isEqualTo(26);
    }

    @Test
    void findByNameFindsCorrectEntity() {
        // Arrange
        TestUser user1 = createUser("Alice", "alice@example.com", 25);
        TestUser user2 = createUser("Bob", "bob@example.com", 30);
        userRepository.save(user1);
        userRepository.save(user2);

        // Act
        TestUser found = userRepository.findByName("Alice");

        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Alice");
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void findByEmailFindsCorrectEntity() {
        // Arrange
        TestUser user = createUser("Test", "test@example.com", 25);
        userRepository.save(user);

        // Act
        TestUser found = userRepository.findByEmail("test@example.com");

        // Assert
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(user.getId());
    }

    @Test
    void findAllByAgeReturnsMatchingEntities() {
        // Arrange
        userRepository.save(createUser("Alice", "alice@example.com", 25));
        userRepository.save(createUser("Bob", "bob@example.com", 30));
        userRepository.save(createUser("Charlie", "charlie@example.com", 25));

        // Act
        List<TestUser> age25Users = userRepository.findAllByAge(25);

        // Assert
        assertThat(age25Users).hasSize(2);
        assertThat(age25Users).extracting(TestUser::getName)
                .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void countReturnsCorrectCount() {
        // Arrange
        userRepository.save(createUser("User1", "user1@example.com", 25));
        userRepository.save(createUser("User2", "user2@example.com", 30));
        userRepository.save(createUser("User3", "user3@example.com", 35));

        // Act
        long count = userRepository.count();

        // Assert
        assertThat(count).isEqualTo(3);
    }

    private TestUser createUser(String name, String email, int age) {
        TestUser user = new TestUser();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail(email);
        user.setAge(age);
        return user;
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.database.repository", createInstance = false)
    static class TestRoot {
    }

    @Entity(table = "TEST_USERS")
    @Data
    public static class TestUser {

        @Id
        private UUID id;

        @Column
        private String name;

        @Column
        private String email;

        @Column
        private Integer age;
    }

    @Repository
    public interface TestUserRepository extends CrudRepository<TestUser, UUID> {
        TestUser findByName(String name);
        TestUser findByEmail(String email);
        List<TestUser> findAllByAge(int age);
    }
}
