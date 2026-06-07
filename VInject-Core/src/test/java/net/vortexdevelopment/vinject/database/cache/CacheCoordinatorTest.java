package net.vortexdevelopment.vinject.database.cache;

import lombok.Data;
import net.vortexdevelopment.vinject.annotation.Bean;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.component.Service;
import net.vortexdevelopment.vinject.annotation.database.AutoLoad;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.EnableCaching;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.database.RegisterCacheContributor;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CacheCoordinatorTest {

    private TestApplicationContext context;
    private Database database;
    private CacheCoordinator coordinator;
    private AutoLoadRepository repository;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        database = MockDatabaseBuilder.createInMemory("coordinator_test");
        
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .build();
        
        coordinator = context.getComponent(CacheCoordinator.class);
        repository = context.getComponent(AutoLoadRepository.class);
        cacheManager = context.getComponent(CacheManager.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testAutoLoadCoordination() {
        // 1. Prepare data in DB
        UUID playerId = UUID.randomUUID();
        AutoLoadEntity entity = new AutoLoadEntity();
        entity.setId(UUID.randomUUID());
        entity.setPlayerId(playerId);
        entity.setScore(100);
        
        // Save via repository (this will also cache it, so let's clear the cache after)
        repository.save(entity);

        // Check the cache to make sure the entity is cached
        assertThat(cacheManager.getCache(AutoLoadRepository.class.getName()).get(entity.getId())).isNotNull();

        // Now invalidate to simulate fresh load
        cacheManager.getCache(AutoLoadRepository.class.getName()).invalidate();
        assertThat(cacheManager.getCache(AutoLoadRepository.class.getName()).size()).isZero();

        // 2. Trigger coordination load
        coordinator.load("player_uuid", playerId);

        // 3. Verify it's now in cache
        Object cached = cacheManager.getCache(AutoLoadRepository.class.getName()).get(entity.getId());
        assertThat(cached).isNotNull();
        assertThat(((AutoLoadEntity)cached).getPlayerId()).isEqualTo(playerId);
    }

    @Test
    void testAutoUnloadCoordination() {
        // 1. Prepare and cache data
        UUID playerId = UUID.randomUUID();
        AutoLoadEntity entity = new AutoLoadEntity();
        entity.setId(UUID.randomUUID());
        entity.setPlayerId(playerId);
        repository.save(entity);
        
        assertThat(cacheManager.getCache(AutoLoadRepository.class.getName()).get(entity.getId())).isNotNull();

        // 2. Trigger coordination unload
        coordinator.unload("player_uuid", playerId);

        // 3. Verify removed from cache
        assertThat(cacheManager.getCache(AutoLoadRepository.class.getName()).get(entity.getId())).isNull();
    }

    @Test
    void testManualContributor() {
        // The contributor is registered via @RegisterCacheContributor in the static class below
        UUID mockId = UUID.randomUUID();
        
        // Trigger load
        coordinator.load("manual_namespace", mockId);
        
        // Verify the contributor was called and injected into cache
        // Note: ManualContributor injects into AutoLoadRepository's cache in this test setup
        Cache<Object, Object> cache = cacheManager.getCache(AutoLoadRepository.class.getName());
        AutoLoadEntity manualInjected = (AutoLoadEntity) cache.get(mockId);
        
        assertThat(manualInjected).isNotNull();
        assertThat(manualInjected.getPlayerId()).isEqualTo(mockId);
    }

    @Root(packageName = "net.vortexdevelopment.vinject.database.cache", createInstance = false)
    static class TestRoot {}

    @Entity(table = "auto_load_entities")
    @Data
    public static class AutoLoadEntity {
        @Id
        private UUID id;

        @Column
        @AutoLoad("player_uuid")
        private UUID playerId;

        @Column
        private Integer score;
    }

    @Repository
    @EnableCaching
    public interface AutoLoadRepository extends CrudRepository<AutoLoadEntity, UUID> {}

    @RegisterCacheContributor
    public static class ManualContributor implements CacheContributor<AutoLoadEntity> {
        @Override
        public String getNamespace() {
            return "manual_namespace";
        }

        @Override
        public void contribute(Object value, CacheProvider<AutoLoadEntity> provider) {
            AutoLoadEntity entity = new AutoLoadEntity();
            entity.setId((UUID) value);
            entity.setPlayerId((UUID) value);
            provider.accept(entity);
        }
    }
}
