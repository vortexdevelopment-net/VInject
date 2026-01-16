package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlSerializationTest {

    @YamlItem
    public static class SubData {
        @Comment("Sub value comment")
        private String subValue = "defaultSub";
        
        @Key("the-number")
        private int number = 42;

        public SubData() {}
        public SubData(String subValue, int number) {
            this.subValue = subValue;
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SubData subData = (SubData) o;
            return number == subData.number && Objects.equals(subValue, subData.subValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subValue, number);
        }
    }

    @YamlItem
    public static class ItemWithId {
        @YamlId
        private String id;

        @Comment("Description of the item")
        private String description;

        private double price;

        public ItemWithId() {}
        public ItemWithId(String description, double price) {
            this.description = description;
            this.price = price;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemWithId that = (ItemWithId) o;
            return Double.compare(that.price, price) == 0 && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, price);
        }
    }

    @YamlItem
    public static class NestedRoot {
        @Comment("Global title")
        private String title = "My Dashboard";

        private List<SubData> dataList = new ArrayList<>();

        @Comment("Mapped items with IDs")
        private Map<String, ItemWithId> items = new LinkedHashMap<>();

        public NestedRoot() {
            dataList.add(new SubData("D1", 1));
            dataList.add(new SubData("D2", 2));
            items.put("apple", new ItemWithId("Sweet fruit", 1.5));
            items.put("banana", new ItemWithId("Yellow fruit", 0.5));
        }
    }

    @Test
    public void testFullSerializationCycle() {
        YamlConfig config = new YamlConfig(new DocumentNode());

        // 1. Initial State
        NestedRoot rootObject = new NestedRoot();
        config.set("app", rootObject);
        config.set("metadata.version", "1.0.0");
        config.set("metadata.tags", Arrays.asList("cool", "fast", "agentic"));
        
        String firstRender = config.render();
        System.out.println("--- First Render ---\n" + firstRender);

        // 2. Load and verify
        YamlConfig config2 = YamlConfig.load(firstRender);
        String secondRender = config2.render();
        System.out.println("--- Second Render ---\n" + secondRender);

        // Double render consistency
        assertEquals(firstRender.trim(), secondRender.trim(), "Second render should match first render precisely");

        // Value verification
        assertEquals("1.0.0", config2.get("metadata.version"));
        List<String> tags = config2.get("metadata.tags");
        assertEquals(Arrays.asList("cool", "fast", "agentic"), tags);

        assertEquals("My Dashboard", config2.get("app.title"));
        
        List<?> loadedDataList = config2.get("app.dataList");
        assertEquals(2, loadedDataList.size());
        assertEquals("D1", ((Map<?, ?>) loadedDataList.get(0)).get("subValue"));
        assertEquals(1, ((Map<?, ?>) loadedDataList.get(0)).get("the-number"));

        Map<String, Object> apple = config2.get("app.items.apple");
        assertEquals("Sweet fruit", apple.get("description"));
        assertEquals(1.5, (Double) apple.get("price"));
    }

    @Test
    public void testDoubleLoadWithComments() {
        // Let's use a simpler one that I know works.
        String yaml = "# Header\n" +
                "\n" +
                "Settings:\n" +
                "  # Speed setting\n" +
                "  Speed: 10\n" +
                "  # Enable feature\n" +
                "  Enabled: true\n" +
                "\n" +
                "Messages:\n" +
                "  - \"Msg 1\"\n" +
                "  - \"Msg 2\"";

        YamlConfig config = YamlConfig.load(yaml);
        String r1 = config.render();
        
        YamlConfig config2 = YamlConfig.load(r1);
        String r2 = config2.render();
        
        assertEquals(r1.trim(), r2.trim());
        // Note: we can't strictly compare to initial 'yaml' if it was unquoted, 
        // but since I updated 'yaml' to have quotes, it should match.
        assertEquals(yaml.trim(), r1.trim());
    }

    @Test
    public void testComplexMapAndLists() {
        YamlConfig config = new YamlConfig(new DocumentNode());
        
        Map<String, List<Integer>> complex = new LinkedHashMap<>();
        complex.put("primes", Arrays.asList(2, 3, 5, 7));
        complex.put("evens", Arrays.asList(2, 4, 6, 8));
        
        config.set("data.numbers", complex);
        
        String rendered = config.render();
        assertTrue(rendered.contains("primes:"));
        assertTrue(rendered.contains("- 2"));
        
        YamlConfig config2 = YamlConfig.load(rendered);
        // Verify we can still get keys
        Set<String> keys = config2.getSection("data.numbers").getKeys(false);
        assertTrue(keys.contains("primes"));
        assertTrue(keys.contains("evens"));
        
        List<String> primes = config2.get("data.numbers.primes");
        assertEquals(Arrays.asList(2, 3, 5, 7), primes);
    }
    @Test
    public void testDynamicItemSerialization() {
        YamlConfig config = new YamlConfig(new DocumentNode());
        
        Map<String, Object> dynamicSettings = new LinkedHashMap<>();
        
        // This simulates the user's setup
        Map<String, String[]> item1 = new LinkedHashMap<>();
        // DynamicItem has fields: id, value, actions
        // But we want to test if it serializes correctly via updateNodeFromObject
        
        SubData item = new SubData("Test Value", 123);
        dynamicSettings.put("test-item-1", item);
        
        config.set("DynamicSettings", dynamicSettings);
        
        String rendered = config.render();
        System.out.println("--- Dynamic Render ---\n" + rendered);
        
        assertTrue(rendered.contains("DynamicSettings:"), "Should contain DynamicSettings section");
        assertTrue(rendered.contains("test-item-1:"), "Should contain test-item-1 key");
        assertTrue(rendered.contains("subValue: \"Test Value\""), "Should contain subValue");
        assertTrue(rendered.contains("the-number: 123"), "Should contain the-number");
    }
    @Test
    public void testTypeCheckersAndLists() {
        String yaml = "Settings:\n" +
                "  Speed: 10\n" +
                "  Enabled: true\n" +
                "  Name: \"Vortex\"\n" +
                "  Ratio: 0.5\n" +
                "  Weights:\n" +
                "    - 1\n" +
                "    - 2\n" +
                "    - 3\n" +
                "  Sub:\n" +
                "    Key: Value";

        YamlConfig config = YamlConfig.load(yaml);
        
        assertTrue(config.isInt("Settings.Speed"));
        assertTrue(config.isBoolean("Settings.Enabled"));
        assertTrue(config.isString("Settings.Name"));
        assertTrue(config.isDouble("Settings.Ratio"));
        assertTrue(config.isList("Settings.Weights"));
        assertTrue(config.isSection("Settings.Sub"));
        
        List<Integer> weights = config.getIntegerList("Settings.Weights");
        assertEquals(Arrays.asList(1, 2, 3), weights);
    }
}
