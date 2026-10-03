package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlSystemTest {

    @Test
    public void testLosslessParsingAndRendering() {
        String original = "# Header comment\n" +
                "Version: 1.0\n" +
                "\n" +
                "Settings:\n" +
                "  # Comment above key\n" +
                "  Delay: 20\n" +
                "  Enabled: true\n" +
                "\n" +
                "Messages:\n" +
                "  - \"Hello\"\n" +
                "  - \"World\"";

        YamlConfig config = YamlConfig.load(original);
        String rendered = config.render();

        // The exact match depends on how blank lines and comments are rendered.
        // My render() appends \n after children.
        // Let's see.
        System.out.println("Original:\n" + original);
        System.out.println("Rendered:\n" + rendered);

        // Normalize newlines for comparison
        assertEquals(original.trim(), rendered.trim());
    }

    @Test
    public void testInjection() {
        String yaml = "Settings:\n  Delay: 20";
        YamlConfig config = YamlConfig.load(yaml);

        // Inject existing
        config.inject("Settings.Delay", 30, "This is a comment");
        
        // Value should be preserved (20), but comment should be added? 
        // Actually inject documentation says: "Preserve user edited values if key already exists"
        assertEquals(Integer.valueOf(20), ((KeyValueNode)config.getNode("Settings.Delay")).getValue());
        
        // Inject new
        config.inject("Settings.NewKey", "newValue", "Comment for new key");
        assertNotNull(config.getNode("Settings.NewKey"));
        assertEquals("newValue", ((KeyValueNode)config.getNode("Settings.NewKey")).getValue());

        // Inject new section
        config.inject("NewSection.SubKey", "subValue", null);
        assertNotNull(config.getNode("NewSection.SubKey"));
        
        System.out.println("After Injection:\n" + config.render());
    }

    @Test
    public void testListInjection() {
        YamlConfig config = YamlConfig.load("");
        List<String> items = Arrays.asList("item1", "item2");
        config.inject("MyList", items, " List comment");
        
        String rendered = config.render();
        assertTrue(rendered.contains("- \"item1\""));
        assertTrue(rendered.contains("- \"item2\""));
        assertTrue(rendered.contains("# List comment"));
    }
}
