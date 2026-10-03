package net.vortexdevelopment.vinject.database.schema;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.ForeignKey;
import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.annotation.database.Index;
import net.vortexdevelopment.vinject.database.DBUtils;
import net.vortexdevelopment.vinject.database.Database;
import net.vortexdevelopment.vinject.testing.MockDatabaseBuilder;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ForeignKeyAndIndexSchemaTest {
    private Database database;
    private TestApplicationContext context;

    @BeforeEach
    void setUp() {
        Database.setTablePrefix("schema_");
        database = MockDatabaseBuilder.createInMemory("foreign_key_index_schema");
        context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withDatabase(database)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
        if (database != null) database.shutdown();
        Database.setTablePrefix("example_");
    }

    @Test
    void createsFieldAndCompositeIndexes() throws Exception {
        try (Connection connection = database.getConnection()) {
            Map<String, DBUtils.IndexInfo> indexes = DBUtils.getExistingIndexes(connection, "schema_island_upgrades");

            assertThat(indexes.values()).anyMatch(index ->
                    !index.unique() && equalsColumns(index, "island_id"));
            assertThat(indexes.values()).anyMatch(index ->
                    index.unique() && equalsColumns(index, "island_id", "upgrade_type"));
        }
    }

    @Test
    void createsForeignKeysAndAppliesDeleteAndUpdateActions() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID updatedIslandId = UUID.randomUUID();
        UUID upgradeId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        try (Connection connection = database.getConnection()) {
            Map<String, DBUtils.ForeignKeyInfo> upgradeKeys =
                    DBUtils.getExistingForeignKeys(connection, "schema_island_upgrades");
            Map<String, DBUtils.ForeignKeyInfo> playerKeys =
                    DBUtils.getExistingForeignKeys(connection, "schema_players");

            assertThat(upgradeKeys.values()).singleElement().satisfies(key -> {
                assertThat(key.onDelete()).isEqualTo(ForeignKeyAction.CASCADE);
                assertThat(key.onUpdate()).isEqualTo(ForeignKeyAction.CASCADE);
            });
            assertThat(playerKeys.values()).singleElement().satisfies(key -> {
                assertThat(key.onDelete()).isEqualTo(ForeignKeyAction.SET_NULL);
                assertThat(key.onUpdate()).isEqualTo(ForeignKeyAction.CASCADE);
            });

            execute(connection, "INSERT INTO \"schema_islands\" (\"id\") VALUES (?)", islandId);
            execute(connection,
                    "INSERT INTO \"schema_island_upgrades\" (\"id\", \"island_id\", \"upgrade_type\") VALUES (?, ?, ?)",
                    upgradeId, islandId, "SIZE");
            execute(connection,
                    "INSERT INTO \"schema_players\" (\"id\", \"island_id\") VALUES (?, ?)",
                    playerId, islandId);

            execute(connection, "UPDATE \"schema_islands\" SET \"id\" = ? WHERE \"id\" = ?",
                    updatedIslandId, islandId);
            assertThat(singleUuid(connection,
                    "SELECT \"island_id\" FROM \"schema_players\" WHERE \"id\" = ?", playerId))
                    .isEqualTo(updatedIslandId);

            execute(connection, "DELETE FROM \"schema_islands\" WHERE \"id\" = ?", updatedIslandId);
            assertThat(singleLong(connection, "SELECT COUNT(*) FROM \"schema_island_upgrades\""))
                    .isZero();
            assertThat(singleUuid(connection,
                    "SELECT \"island_id\" FROM \"schema_players\" WHERE \"id\" = ?", playerId))
                    .isNull();
        }
    }

    private static boolean equalsColumns(DBUtils.IndexInfo index, String... columns) {
        if (index.columns().size() != columns.length) return false;
        for (int i = 0; i < columns.length; i++) {
            if (!index.columns().get(i).equalsIgnoreCase(columns[i])) return false;
        }
        return true;
    }

    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static UUID singleUuid(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private static long singleLong(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    @Root(packageName = "net.vortexdevelopment.vinject.database.schema", createInstance = false)
    static class TestRoot {
    }

    @Entity(table = "islands")
    public static class Island {
        @Id
        private UUID id;
    }

    @Entity(table = "island_upgrades")
    @Index(columns = {"islandId", "upgrade_type"}, unique = true)
    public static class IslandUpgrade {
        @Id
        private UUID id;

        @Index
        @ForeignKey(entity = Island.class, onDelete = ForeignKeyAction.CASCADE,
                onUpdate = ForeignKeyAction.CASCADE)
        @Column(name = "island_id", nullable = false)
        private UUID islandId;

        @Column(name = "upgrade_type", nullable = false)
        private String upgradeType;
    }

    @Entity(table = "players")
    public static class Player {
        @Id
        private UUID id;

        @Index
        @ForeignKey(entity = Island.class, onDelete = ForeignKeyAction.SET_NULL,
                onUpdate = ForeignKeyAction.CASCADE)
        @Column(name = "island_id")
        private UUID islandId;
    }
}
