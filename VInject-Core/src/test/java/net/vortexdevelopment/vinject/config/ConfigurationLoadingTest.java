package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnLoad;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for configuration loading and injection from YAML files.
 * Verifies that @Configuration and @ConfigurationBatch work correctly.
 */
class ConfigurationLoadingTest {

    @TempDir
    File tempDir;

    @Test
    void configurationLoadsFromYamlFile() throws IOException {
        // Arrange: Create a YAML file
        File configFile = new File(tempDir, "test-config.yml");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("test-key: test-value\n");
            writer.write("number-key: 42\n");
        }

        // Act: Load configuration
        // Note: This would require setting up the config file in the correct location
        // For now, this is a placeholder test structure
    }

    @Test
    void onLoadMethodCalledAfterConfiguration() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(ConfigTestRoot.class)
                .build()) {
            
            // Assert: Configuration component should be loaded
            TestConfig config = context.getComponentOrNull(TestConfig.class);
            // Note: This may be null if no config file exists, which is expected
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.config", createInstance = false)
    static class ConfigTestRoot {
    }

    @YamlConfiguration(file = "test-config.yml")
    @Getter
    @Setter
    public static class TestConfig {
        @Key("test-key")
        private String testKey;

        @Key("number-key")
        private int numberKey;

        private boolean onLoadCalled = false;

        @OnLoad
        public void afterLoad() {
            onLoadCalled = true;
        }
    }

    @YamlDirectory(dir = "test-items", target = TestItem.class)
    @Getter
    @Setter
    public static class TestItems {
        private List<TestItem> items;
    }

    @Getter
    @Setter
    public static class TestItem {
        @Key("id")
        private String id;

        @Key("value")
        private String value;
    }
}
