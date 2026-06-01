package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlBlockScalarTest {

    @Test
    void parsesLiteralBlockWithIndentedLines() {
        String yaml = """
                rate-limit:
                  throttle-disconnect-message: |
                    §cBlackwall

                    §7You are joining too fast, please slow down.
                  max-connections-per-second: 30
                """;

        YamlConfig config = YamlConfig.load(yaml);
        assertEquals(
                "§cBlackwall\n\n§7You are joining too fast, please slow down.",
                config.get("rate-limit.throttle-disconnect-message")
        );
        assertEquals(Integer.valueOf(30), config.get("rate-limit.max-connections-per-second"));
    }

    @Test
    void roundTripsLiteralBlock() {
        String expected = "§cBlackwall\n\n§7You are joining too fast, please slow down.";
        YamlConfig config = new YamlConfig(new DocumentNode());
        config.set("message", expected);

        String rendered = config.render();
        assertTrue(rendered.contains("message: |"));
        assertTrue(rendered.contains("  §cBlackwall"));
        assertTrue(rendered.contains("  §7You are joining too fast, please slow down."));

        YamlConfig loaded = YamlConfig.load(rendered);
        assertEquals(expected, loaded.get("message"));
    }

    @Test
    void parsesFoldedBlock() {
        String yaml = """
                text: >
                  Hello
                  world
                next: 1
                """;

        YamlConfig config = YamlConfig.load(yaml);
        assertEquals("Hello world", config.get("text"));
        assertEquals(Integer.valueOf(1), config.get("next"));
    }

    @Test
    void stripChompingRemovesTrailingNewlines() {
        String yaml = """
                text: |-
                  line
                """;

        YamlConfig config = YamlConfig.load(yaml);
        assertEquals("line", config.get("text"));
        assertFalse(config.get("text").toString().endsWith("\n"));
    }

    @Test
    void parsesEmptyLiteralBlock() {
        String yaml = """
                text: |
                next: true
                """;

        YamlConfig config = YamlConfig.load(yaml);
        assertEquals("", config.get("text"));
        assertEquals(true, config.get("next"));
    }
}
