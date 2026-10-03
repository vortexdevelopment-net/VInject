package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomSerializerTest {

    // A reference type with NO @YamlItem or @YamlConfiguration
    public static class ExternalType {
        private String name;
        private int value;

        public ExternalType(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public int getValue() { return value; }

        @Override
        public String toString() {
            return "ShouldNotUseThisToString";
        }
    }

    public static class ExternalTypeSerializer implements YamlSerializerBase<ExternalType> {
        @Override
        public Class<ExternalType> getTargetType() {
            return ExternalType.class;
        }

        @Override
        public Map<String, Object> serialize(ExternalType instance) {
            Map<String, Object> map = new HashMap<>();
            map.put("custom-name", instance.getName());
            map.put("custom-value", instance.getValue());
            map.put("marker", "serialized-by-custom");
            return map;
        }

        @Override
        public ExternalType deserialize(Map<String, Object> map) {
            String name = (String) map.get("custom-name");
            int value = ((Number) map.get("custom-value")).intValue();
            return new ExternalType(name, value);
        }
    }

    @Test
    public void testCustomSerializerPresence() {
        YamlSerializerRegistry.registerSerializer(new ExternalTypeSerializer());
        
        YamlConfig config = new YamlConfig(new DocumentNode());
        ExternalType item = new ExternalType("TestItem", 123);
        
        config.set("my-data", item);
        
        String rendered = config.render();
        
        // Check that it's NOT using toString()
        assertFalse(rendered.contains("ShouldNotUseThisToString"), "Should not have used toString()");
        
        // Check that custom keys from serializer are present
        assertTrue(rendered.contains("custom-name: \"TestItem\""));
        assertTrue(rendered.contains("custom-value: 123"));
        assertTrue(rendered.contains("marker: \"serialized-by-custom\""));
        
        // Test Deserialization
        YamlConfig config2 = YamlConfig.load(rendered);
        ExternalType loaded = config2.get("my-data", ExternalType.class);
        
        assertNotNull(loaded, "Loaded item should not be null");
        assertEquals("TestItem", loaded.getName());
        assertEquals(123, loaded.getValue());
    }
}
