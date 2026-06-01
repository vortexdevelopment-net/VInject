package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDottedMapKeyTest {

    private static final String DOTTED_MAP_KEY = "segment.one.two";

    @Test
    void parsesQuotedDottedKeyAsSingleMapEntry() {
        String yaml = """
                parent:
                  entries:
                    'segment.one.two':
                      value: "alpha"
                      enabled: true
                """;

        YamlConfig config = YamlConfig.load(yaml);
        @SuppressWarnings("unchecked")
        Map<String, Object> entries = config.get("parent.entries", Map.class);
        assertNotNull(entries);
        assertTrue(entries.containsKey(DOTTED_MAP_KEY), "Keys were: " + entries.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) entries.get(DOTTED_MAP_KEY);
        assertEquals("alpha", entry.get("value"));
        assertEquals(true, entry.get("enabled"));
    }

    @Test
    void roundTripsDottedMapKeyWithoutSplittingIntoNestedSections() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("value", "alpha");
        entry.put("enabled", true);

        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put(DOTTED_MAP_KEY, entry);

        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("entries", entries);

        YamlConfig config = (YamlConfig) YamlConfig.fromMap(Map.of("parent", parent));
        String rendered = config.render();

        assertTrue(rendered.contains("'segment.one.two':"), "Rendered:\n" + rendered);
        assertTrue(!rendered.contains("one:\n    two:"), "Should not nest by dot segments:\n" + rendered);

        YamlConfig loaded = YamlConfig.load(rendered);
        @SuppressWarnings("unchecked")
        Map<String, Object> loadedEntries = loaded.get("parent.entries", Map.class);
        assertEquals("alpha", ((Map<?, ?>) loadedEntries.get(DOTTED_MAP_KEY)).get("value"));
    }

    @Test
    void nestedAnnotationKeyStillCreatesSections() {
        String yaml = """
                root:
                  Modules:
                    Blocks:
                      Enabled: true
                """;

        YamlConfig config = YamlConfig.load(yaml);
        assertEquals(true, config.get("root.Modules.Blocks.Enabled"));
    }
}
