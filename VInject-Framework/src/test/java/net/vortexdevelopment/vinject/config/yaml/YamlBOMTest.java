package net.vortexdevelopment.vinject.config.yaml;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class YamlBOMTest {

    @Test
    public void testYamlWithBOM() {
        // \uFEFF is the Byte Order Mark (BOM)
        String yamlWithBOM = "\uFEFF# Specify custom colors. Can be used in every message send to the chat.\n" +
                "ColorSettings:\n" +
                "  Primary: \"#ff0000\"";

        try {
            YamlConfig config = YamlConfig.load(yamlWithBOM);
            assertNotNull(config);
            assertEquals("#ff0000", config.get("ColorSettings.Primary"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Unsupported YAML syntax at line")) {
                fail("YamlParser failed to handle BOM: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }
}
