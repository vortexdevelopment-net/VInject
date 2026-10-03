package net.vortexdevelopment.vinject.database.repository;

import net.vortexdevelopment.vinject.annotation.database.Column;
import net.vortexdevelopment.vinject.annotation.database.Entity;
import net.vortexdevelopment.vinject.annotation.database.Id;
import net.vortexdevelopment.vinject.database.serializer.SerializerRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMetadataTest {

    @Test
    void mapsColumnNameOnIdField() {
        EntityMetadata metadata = new EntityMetadata(IdWithNamedColumn.class, new SerializerRegistry());

        assertThat(metadata.getPrimaryKeyColumn()).isEqualTo("player_uuid");
        assertThat(metadata.getColumnName("playerUuid")).isEqualTo("player_uuid");
        assertThat(metadata.getPrimaryKeyField().getName()).isEqualTo("playerUuid");
    }

    @Entity(table = "metadata_id_test")
    private static final class IdWithNamedColumn {
        @Id
        @Column(name = "player_uuid", nullable = false)
        private UUID playerUuid;
    }
}
