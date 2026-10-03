package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.ConfigurationSection;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlParserQuotedKeyTest {

    @Test
    public void normalizeMappingKey_stripsQuotes() {
        assertEquals("false", YamlParser.normalizeMappingKey("'false'"));
        assertEquals("false", YamlParser.normalizeMappingKey("\"false\""));
        assertEquals("true", YamlParser.normalizeMappingKey("'true'"));
        assertEquals("no", YamlParser.normalizeMappingKey("no"));
    }

    @Test
    public void normalizeMappingKey_singleQuotedEscapedApostrophe() {
        assertEquals("it's", YamlParser.normalizeMappingKey("'it''s'"));
    }

    @Test
    public void quotedFalseKey_becomesBareFalseInSection() {
        String yaml = "root:\n  'false':\n    Material: PAPER\n";
        YamlConfig config = YamlConfig.load(yaml);
        ConfigurationSection root = config.getSection("root");
        Set<String> keys = root.getKeys(false);
        assertTrue(keys.contains("false"), "Keys were: " + keys);
        assertEquals("PAPER", root.getConfigurationSection("false").getString("Material"));
    }

    @Test
    public void parsesNamespacedMappingKeys() {
        YamlConfig config = YamlConfig.load("""
                vortexskyblock:member:
                  Display Name: Member
                Permissions:
                  vortexskyblock:island_build: true
                """);

        assertEquals(Set.of("vortexskyblock:member", "Permissions"), config.getKeys(false));
        assertEquals("Member", config.get("vortexskyblock:member.Display Name"));
        assertEquals(Boolean.TRUE, config.get("Permissions.vortexskyblock:island_build"));
    }

    @Test
    public void preservesNamespacedScalarValues() {
        YamlConfig config = YamlConfig.load("Parent: \"vortexskyblock:member\"\n");

        assertEquals("vortexskyblock:member", config.get("Parent"));
    }
}
