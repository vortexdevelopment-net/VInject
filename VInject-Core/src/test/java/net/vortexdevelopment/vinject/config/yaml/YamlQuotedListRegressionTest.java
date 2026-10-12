package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YamlQuotedListRegressionTest {

    @Test
    void preservesQuotedCommandsLoreAndMappingKeysAcrossSaveAndLoad() throws Exception {
        String yaml;
        try (var input = getClass().getResourceAsStream("/yaml/ultimatekits-quoted-list.yml")) {
            assertNotNull(input);
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        YamlConfig config = YamlConfig.load(yaml);
        for (int round = 0; round < 3; round++) {
            assertEquals(List.of("broadcast Hi: all", "broadcast Hi: all", "broadcast \"Hi: all\"", "don't split: this"), config.get("Commands"));
            assertEquals(List.of("Price: $100", "Path: C:\\kits"), config.get("Lore"));
            assertEquals(List.of("broadcast Hi: all"), config.get("Tagged"));
            assertEquals(List.of("broadcast Hi: all", "broadcast Hi: all"), config.get("Anchored"));
            assertEquals(List.of(Map.of("quoted: key", "value"), Map.of("another: key", "value"), Map.of("Chance", 50)), config.get("Mappings"));
            assertEquals(List.of(), config.get("Empty"));
            config = YamlConfig.load(config.render());
        }
    }
}
