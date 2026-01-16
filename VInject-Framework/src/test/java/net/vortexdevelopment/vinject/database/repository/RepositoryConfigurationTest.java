package net.vortexdevelopment.vinject.database.repository;

import lombok.Data;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.EnableCaching;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.cache.Cache;
import net.vortexdevelopment.vinject.database.cache.CacheConfig;
import net.vortexdevelopment.vinject.database.cache.CacheManager;
import net.vortexdevelopment.vinject.database.cache.CacheManagerImpl;
import net.vortexdevelopment.vinject.database.cache.CachePolicy;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.RepositoryTestUtils;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryConfigurationTest {

    private TestApplicationContext context;
    private Database database;

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
    void testCacheManagerViaBean() {
        database = MockDatabaseBuilder.createInMemory("config_test_bean");
        
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .build();
        
        SimpleRepository repository = context.getComponent(SimpleRepository.class);
        CacheManager cacheManager = context.getComponent(CacheManager.class);
        
        assertThat(cacheManager).isNotNull();
        
        cacheManager.createCache(SimpleRepository.class.getName(), CacheConfig.defaults());
        
        SimpleEntity entity = new SimpleEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Bean Test");
        
        repository.save(entity);
        
        Cache<Object, Object> retCache = cacheManager.getCache(SimpleRepository.class.getName());
        assertThat(retCache).describedAs("Cache '%s' should exist", SimpleRepository.class.getName()).isNotNull();
        assertThat(retCache.get(entity.getId())).describedAs("Entity should be in cache").isNotNull();
    }

    @Test
    void testEnableCachingAnnotation() {
        database = MockDatabaseBuilder.createInMemory("config_test_anno");
        CacheManager cacheManager = new CacheManagerImpl();
        
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .withMock(CacheManager.class, cacheManager) // Inject mock/real manager
                .withComponents(AnnotatedRepository.class)
                .build();
        
        AnnotatedRepository repository = context.getComponent(AnnotatedRepository.class);
        // Refresh cacheManager reference from context as component scanning might have registered a new bean
        cacheManager = context.getComponent(CacheManager.class);
        
        SimpleEntity entity = new SimpleEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Annotation Test");
        
        // This should trigger lazy cache creation
        repository.save(entity);
        
        Cache<Object, Object> cache = cacheManager.getCache(AnnotatedRepository.class.getName());
        
        if (cache != null) {
            Object cached = cache.get(entity.getId());
            assertThat(cached).isNotNull();
        } else {
            assertThat(cache).describedAs("Cache should have been created").isNotNull();
        }
        
        // Verify existence
        assertThat(cacheManager.getCache(AnnotatedRepository.class.getName())).isNotNull();
    }

    @Test
    void testCacheAccessFlow() {
        database = MockDatabaseBuilder.createInMemory("config_test_flow");
        
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .withComponents(AnnotatedRepository.class)
                .build();
        
        AnnotatedRepository repository = context.getComponent(AnnotatedRepository.class);
        // Refresh cacheManager reference
        CacheManager cacheManager = context.getComponent(CacheManager.class);
        
        // 1. Create and Save Entity
        SimpleEntity entity = new SimpleEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Flow Test");
        repository.save(entity);
        
        // Ensure cache is created and populated
        Cache<Object, Object> cache = cacheManager.getCache(AnnotatedRepository.class.getName());
        assertThat(cache).isNotNull();
        assertThat(cache.get(entity.getId())).isNotNull();
        
        // 2. Clear cache to simulate cold start (restart) or eviction
        // Data is still in DB, but not in cache
        cache.invalidate();
        assertThat(cache.get(entity.getId())).isNull();
        
        long initialHits = cache.getHits();
        long initialMisses = cache.getMisses();
        
        // 3. First Read - Should be a MISS (Read from DB, Put in Cache)
        SimpleEntity found1 = repository.findById(entity.getId());
        assertThat(found1).isNotNull();
        assertThat(found1.getName()).isEqualTo("Flow Test");
        
        assertThat(cache.getMisses()).as("First read should be a miss").isEqualTo(initialMisses + 1);
        assertThat(cache.getHits()).as("First read should not be a hit").isEqualTo(initialHits);
        
        // Use a small loop to generate hits
        int hitCount = 5;
        for (int i = 0; i < hitCount; i++) {
            SimpleEntity foundCached = repository.findById(entity.getId());
            assertThat(foundCached).isNotNull();
            assertThat(foundCached.getName()).isEqualTo("Flow Test");
        }
        
        // 4. Verify Hits
        assertThat(cache.getHits()).as("Subsequent reads should be hits").isEqualTo(initialHits + hitCount);
        assertThat(cache.getMisses()).as("Misses should not increase").isEqualTo(initialMisses + 1);
    }
    
    @Test
    void testTTLEviction() throws InterruptedException {
        database = MockDatabaseBuilder.createInMemory("config_test_ttl");
        
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .withComponents(TtlRepository.class)
                .build();
        
        TtlRepository repository = context.getComponent(TtlRepository.class);
        CacheManager cacheManager = context.getComponent(CacheManager.class);
        
        // 1. Save entity
        SimpleEntity entity = new SimpleEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("TTL Test");
        repository.save(entity);
        
        // 2. Verify in cache immediately
        Cache<Object, Object> cache = cacheManager.getCache(TtlRepository.class.getName());
        assertThat(cache).isNotNull();
        assertThat(cache.get(entity.getId())).isNotNull();
        
        // 3. Wait for TTL (1s) to expire + buffer
        Thread.sleep(1200);
        
        // 4. Verify expired from cache (access should return null/miss if checked directly, 
        // or trigger reload if via repo)
        
        // Direct access: TTLCache removes on access if expired
        assertThat(cache.get(entity.getId())).isNull();
        
        // Repo access: Should trigger reload (Miss -> Hit DB -> Put Cache)
        SimpleEntity found = repository.findById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("TTL Test");
        
        // Should be back in cache
        assertThat(cache.get(entity.getId())).isNotNull();
    }

    @Root(packageName = "net.vortexdevelopment.vinject.database.repository", createInstance = false)
    static class TestRoot {}

    @Entity(table = "SIMPLE_ENTITIES")
    @Data
    public static class SimpleEntity {
        @Id
        private UUID id;
        @Column
        private String name;
    }

    @Repository
    public interface SimpleRepository extends CrudRepository<SimpleEntity, UUID> {}

    @Repository
    @EnableCaching(policy = CachePolicy.LRU, maxSize = 500)
    public interface AnnotatedRepository extends CrudRepository<SimpleEntity, UUID> {}

    @Repository
    @EnableCaching(policy = CachePolicy.TTL, ttlSeconds = 1)
    public interface TtlRepository extends CrudRepository<SimpleEntity, UUID> {}

    @Service
    public static class CacheConfigService {
        @Bean
        public CacheManager cacheManager() {
            return new CacheManagerImpl();
        }
    }
}
