package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.ConfigurationValueConverter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlEmptyListLiteralTest {

    @Test
    void deserializeUnquotedEmptyListScalar() {
        assertEquals(List.of(), YamlValueFormatter.deserialize("[]"));
    }

    @Test
    void deserializeQuotedEmptyListScalarAsString() {
        assertEquals("[]", YamlValueFormatter.deserialize("\"[]\""));
    }

    @Test
    void rejectsQuotedEmptyListForListFields() {
        String yaml = """
                filter:
                  whitelist: "[]"
                """;

        YamlConfig config = YamlConfig.load(yaml);
        FilterLists loaded = new FilterLists();
        ConfigurationValueConverter converter = new ConfigurationValueConverter(clazz -> {
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> converter.mapToInstance(config, loaded, FilterLists.class, "filter"));
        assertTrue(error.getMessage().contains("whitelist"));
        assertTrue(error.getMessage().contains("[]"));
    }

    @Test
    void loadsInlineEmptyListSyntax() {
        String yaml = """
                filter:
                  whitelist: []
                  tags:
                    - one
                """;

        YamlConfig config = YamlConfig.load(yaml);
        @SuppressWarnings("unchecked")
        List<String> whitelist = config.get("filter.whitelist", List.class);
        assertNotNull(whitelist);
        assertTrue(whitelist.isEmpty());
        @SuppressWarnings("unchecked")
        List<String> tags = config.get("filter.tags", List.class);
        assertEquals(List.of("one"), tags);
    }

    @Test
    void emptyListRendersAsYamlBracketsNotQuotedString() {
        Map<String, Object> filter = new java.util.LinkedHashMap<>();
        filter.put("whitelist", new ArrayList<>());
        filter.put("blocked-asns", new ArrayList<>());

        YamlConfig config = (YamlConfig) YamlConfig.fromMap(Map.of("filter", filter));
        String rendered = config.render();

        assertTrue(rendered.contains("whitelist: []"), "Rendered:\n" + rendered);
        assertTrue(rendered.contains("blocked-asns: []"), "Rendered:\n" + rendered);
        assertTrue(!rendered.contains("\"[]\""), "Should not quote empty lists:\n" + rendered);
    }

    static class FilterLists {
        List<String> whitelist = new ArrayList<>();
        @net.vortexdevelopment.vinject.annotation.yaml.Key("blocked-asns")
        List<Integer> blockedAsns = new ArrayList<>();
    }
}
