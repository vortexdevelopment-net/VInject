package net.vortexdevelopment.vinject.database.repository;

import lombok.Data;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.util.EnableDebug;
import net.vortexdevelopment.vinject.annotation.util.SetSystemProperty;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.cache.CacheConfig;
import net.vortexdevelopment.vinject.database.cache.CacheManager;
import net.vortexdevelopment.vinject.database.cache.CacheManagerImpl;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.RepositoryTestUtils;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryCacheTest {

    private TestApplicationContext context;
    private Database database;
    private CacheManager cacheManager;
    private CachedUserRepository repository;

    @BeforeEach
    void setUp() {
        database = MockDatabaseBuilder.createInMemory("cache_test");
        cacheManager = new CacheManagerImpl();
        
        // Initialize context
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .build();
        
        // Get the active CacheManager (either default or scanned)
        // Since we didn't explicitly mock it, we rely on scanning finding CacheConfigService (from other test)
        // OR we should be explicit and provide one if we want isolation.
        // To be safe and isolated, we should probably register one if none exists, or accept the scanned one.
        // But for this test, we just need ANY valid CacheManager.
        
        // However, if scanning doesn't find one (e.g. running this test in isolation), we need to provide one.
        // Let's rely on retrieving it, and if missing, we failed to set up correctly.
        // But wait, if I run THIS test alone, RepositoryConfigurationTest might NOT be scanned if scanning is based on classloader/classes present?
        // Both are in the same package.
        
        // Safest approach: Register a CacheManager MOCK (or impl) and Ensure it prevails, 
        // OR just check if one exists, if not register one.
        // But TestApplicationContext structure makes it hard to add after build.
        // Let's use withMock, but assume it MIGHT be overwritten. 
        // If it is overwritten, we just get the new one.
        
        try {
            cacheManager = context.getComponent(CacheManager.class);
        } catch (Exception e) {
            // Not found, so we should have registered it.
            // Let's rebuild context with explicit mock if we want to be sure, 
            // but simpler to just use withMock and then getComponent.
             context.close();
             cacheManager = new CacheManagerImpl();
             context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .withMock(CacheManager.class, cacheManager)
                .build();
             // Even if overwritten, getComponent returns the winner.
             cacheManager = context.getComponent(CacheManager.class);
        }

        // Manually create cache for the repository
        cacheManager.createCache(CachedUserRepository.class.getName(), CacheConfig.defaults());
        
        repository = context.getComponent(CachedUserRepository.class);
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
    void findByIdUsesCache() throws Exception {
        // 1. Save entity
        CachedUser user = new CachedUser();
        user.setId(UUID.randomUUID());
        user.setName("Original Name");
        repository.save(user);

        // 2. Verify it's in cache
        Object cached = cacheManager.getCache(CachedUserRepository.class.getName()).get(user.getId());
        assertThat(cached).isNotNull();
        assertThat(((CachedUser)cached).getName()).isEqualTo("Original Name");

        // 3. Manually update DB (bypassing repo/cache)
        updateUserInDb(user.getId(), "Updated Name");

        // 4. Find via repo - should return CACHED value (Original Name)
        CachedUser found = repository.findById(user.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Original Name");
        
        // 5. Invalidate cache
        cacheManager.getCache(CachedUserRepository.class.getName()).remove(user.getId());
        
        // 6. Find via repo - should return DB value (Updated Name)
        CachedUser foundAfterEvict = repository.findById(user.getId());
        assertThat(foundAfterEvict).isNotNull();
        assertThat(foundAfterEvict.getName()).isEqualTo("Updated Name");
    }

    @Test
    void saveUpdatesCache() {
        // 1. Save entity
        CachedUser user = new CachedUser();
        user.setId(UUID.randomUUID());
        user.setName("Initial");
        repository.save(user);

        // 2. Update entity via repo
        user.setName("Updated");
        repository.save(user);

        // 3. Verify cache has updated value
        Object cached = cacheManager.getCache(CachedUserRepository.class.getName()).get(user.getId());
        assertThat(cached).isNotNull();
        assertThat(((CachedUser)cached).getName()).isEqualTo("Updated");
        
        // 4. Verify findById returns updated value
        CachedUser found = repository.findById(user.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteRemovesFromCache() {
        // 1. Save entity
        CachedUser user = new CachedUser();
        user.setId(UUID.randomUUID());
        user.setName("To Delete");
        repository.save(user);

        // 2. Verify in cache
        assertThat(cacheManager.getCache(CachedUserRepository.class.getName()).get(user.getId())).isNotNull();

        // 3. Delete via repo
        repository.deleteById(user.getId());

        // 4. Verify removed from cache
        assertThat(cacheManager.getCache(CachedUserRepository.class.getName()).get(user.getId())).isNull();
    }
    
    @Test
    void findAllPopulatesCache() {
        // 1. Create entities
        CachedUser user1 = new CachedUser();
        user1.setId(UUID.randomUUID());
        user1.setName("User 1");
        
        CachedUser user2 = new CachedUser();
        user2.setId(UUID.randomUUID());
        user2.setName("User 2");
        
        repository.save(user1);
        repository.save(user2);
        
        // Clear cache to simulate clean slate
        cacheManager.getCache(CachedUserRepository.class.getName()).invalidate();
        assertThat(cacheManager.getCache(CachedUserRepository.class.getName()).size()).isZero();
        
        // 2. Call findAll
        repository.findAll();
        
        // 3. Verify cache is populated
        assertThat(cacheManager.getCache(CachedUserRepository.class.getName()).get(user1.getId())).isNotNull();
        assertThat(cacheManager.getCache(CachedUserRepository.class.getName()).get(user2.getId())).isNotNull();
    }

    private void updateUserInDb(UUID id, String newName) throws Exception {
        String tableName = context.getDatabase().getSchemaFormatter().formatTableName(Database.getTablePrefix() + "CACHED_USERS");
        context.getDatabase().connect(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE " + tableName + " SET name = ? WHERE id = ?")) {
                stmt.setString(1, newName);
                stmt.setObject(2, id);
                stmt.executeUpdate();
            }
        });
    }

    @Root(packageName = "net.vortexdevelopment.vinject.database.repository", createInstance = false)
    static class TestRoot {}

    @Entity(table = "CACHED_USERS")
    @Data
    public static class CachedUser {
        @Id
        private UUID id;

        @Column
        private String name;
    }

    @Repository
    @EnableDebug
    public interface CachedUserRepository extends CrudRepository<CachedUser, UUID> {}
}
