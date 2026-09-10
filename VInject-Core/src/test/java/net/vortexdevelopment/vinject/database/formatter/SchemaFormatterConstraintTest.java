package net.vortexdevelopment.vinject.database.formatter;

import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaFormatterConstraintTest {

    @Test
    void formatsMySqlIndexesAndForeignKeys() {
        SchemaFormatter formatter = new MySQLSchemaFormatter();

        assertThat(formatter.formatCreateIndex("players", "idx_players_island", List.of("island_id"), false))
                .isEqualTo("CREATE INDEX `idx_players_island` ON `players` (`island_id`)");
        assertThat(formatter.formatAddForeignKey(
                "players", "fk_players_island", "island_id", "islands", "id",
                ForeignKeyAction.SET_NULL, ForeignKeyAction.CASCADE))
                .isEqualTo("ALTER TABLE `players` ADD CONSTRAINT `fk_players_island` "
                        + "FOREIGN KEY (`island_id`) REFERENCES `islands` (`id`) "
                        + "ON DELETE SET NULL ON UPDATE CASCADE");
        assertThat(formatter.formatDropForeignKey("players", "fk_players_island"))
                .isEqualTo("ALTER TABLE `players` DROP FOREIGN KEY `fk_players_island`");
    }

    @Test
    void formatsH2IndexesAndForeignKeys() {
        SchemaFormatter formatter = new H2SchemaFormatter();

        assertThat(formatter.formatCreateIndex(
                "upgrades", "uidx_upgrade", List.of("island_id", "upgrade_type"), true))
                .isEqualTo("CREATE UNIQUE INDEX \"uidx_upgrade\" ON \"upgrades\" "
                        + "(\"island_id\", \"upgrade_type\")");
        assertThat(formatter.formatDropIndex("upgrades", "uidx_upgrade"))
                .isEqualTo("DROP INDEX \"uidx_upgrade\"");
        assertThat(formatter.formatDropForeignKey("players", "fk_players_island"))
                .isEqualTo("ALTER TABLE \"players\" DROP CONSTRAINT \"fk_players_island\"");
    }
}
