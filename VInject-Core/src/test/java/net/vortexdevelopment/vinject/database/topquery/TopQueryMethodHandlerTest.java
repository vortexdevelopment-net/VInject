package net.vortexdevelopment.vinject.database.topquery;

import lombok.Data;
import net.vortexdevelopment.vinject.annotation.component.Repository;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.database.repository.CrudRepository;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.RepositoryTestUtils;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopQueryMethodHandlerTest {

    private TestApplicationContext context;
    private StatsRepository repository;
    private Database database;

    @BeforeEach
    void setUp() {
        this.database = MockDatabaseBuilder.createInMemory("top_query_test");
        this.context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(this.database)
                .build();
        this.repository = this.context.getComponent(StatsRepository.class);
    }

    @AfterEach
    void tearDown() {
        if (this.database != null) {
            RepositoryTestUtils.clearDatabase(this.database);
        }
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void findTop10ByOrderByTriggersDescUsesFullFieldName() {
        this.repository.save(new Stats(1L, 2L));
        this.repository.save(new Stats(2L, 8L));

        assertThat(this.repository.findTop10ByOrderByTriggersDesc())
                .extracting(Stats::getTriggers)
                .containsExactly(8L, 2L);
    }

    @Root(packageName = "net.vortexdevelopment.vinject.database.topquery", createInstance = false)
    static class TestRoot {
    }

    @Entity(table = "top_query_stats")
    @Data
    public static class Stats {

        @Id
        private Long id;

        @Column
        private Long triggers;

        public Stats() {
        }

        public Stats(Long id, Long triggers) {
            this.id = id;
            this.triggers = triggers;
        }
    }

    @Repository
    public interface StatsRepository extends CrudRepository<Stats, Long> {
        List<Stats> findTop10ByOrderByTriggersDesc();
    }
}
