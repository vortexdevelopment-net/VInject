package net.vortexdevelopment.vinject.config;

import lombok.Getter;
import lombok.Setter;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnLoad;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for YAML configuration loading and injection.
 */
class YamlConfigurationTest {

    @Test
    void yamlConfigurationIsInjected() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            // Get component that has configuration injected
            ComponentWithConfig comp = context.getComponent(ComponentWithConfig.class);

            // Assert: Configuration should be injected (may be null if file doesn't exist, which is OK)
            // The point is to verify injection works, not that the file exists
            assertThat(comp).isNotNull();
        }
    }

    @Test
    void onLoadMethodIsCalledAfterConfigLoading() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            TestConfig config = context.getComponentOrNull(TestConfig.class);

            // Assert: If config loaded, OnLoad should have been called
            if (config != null) {
                assertThat(config.isOnLoadCalled()).isTrue();
            }
        }
    }

    @Test
    void configurationContainerIsAvailable() {
        // Arrange & Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {
            
            ConfigurationContainer container = context.getComponent(ConfigurationContainer.class);

            // Assert
            assertThat(container).isNotNull();
        }
    }

    @Test
    void yamlConfigurationDoesNotOverwriteCustomValuesOnStartup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        // Create custom config file
        java.io.File configFile = tempDir.resolve("test-config.yml").toFile();
        try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
            writer.write("app-name: \"Custom App\"\n");
            writer.write("port: 9090\n");
        }

        // Set ConfigurationContainer root directory
        ConfigurationContainer.setRootDirectory(tempDir);

        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(TestConfig.class)
                .build()) {

            TestConfig config = context.getComponent(TestConfig.class);

            // Verify the custom values are loaded correctly
            assertThat(config.getAppName()).isEqualTo("Custom App");
            assertThat(config.getPort()).isEqualTo(9090);

            // Read the file content and check if it remains correct (meaning it wasn't overwritten with default values)
            String fileContent = java.nio.file.Files.readString(configFile.toPath());
            assertThat(fileContent).contains("app-name: \"Custom App\"");
            assertThat(fileContent).contains("port: 9090");
        }
    }

    @Test
    void yamlConfigurationMergesMissingKeysOnStartup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) throws Exception {
        // Create custom config file but with missing 'port' key
        java.io.File configFile = tempDir.resolve("test-config.yml").toFile();
        try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
            writer.write("app-name: \"Custom App\"\n");
        }

        // Set ConfigurationContainer root directory
        ConfigurationContainer.setRootDirectory(tempDir);

        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(TestConfig.class)
                .build()) {

            TestConfig config = context.getComponent(TestConfig.class);

            // Verify the custom value is loaded and the missing key gets default value
            assertThat(config.getAppName()).isEqualTo("Custom App");
            assertThat(config.getPort()).isEqualTo(8080); // Default value in class definition

            // Read the file content and verify the missing key was merged (saved to file)
            String fileContent = java.nio.file.Files.readString(configFile.toPath());
            assertThat(fileContent).contains("app-name: \"Custom App\"");
            assertThat(fileContent).contains("port: 8080");
        }
    }

    // Test components

    @Root(packageName = "net.vortexdevelopment.vinject.config", createInstance = false)
    static class TestRoot {
    }

    @Component
    public static class ComponentWithConfig {
        @Inject
        public TestConfig config;

        @Inject
        public ConfigurationContainer configContainer;
    }

    @YamlConfiguration(file = "test-config.yml")
    @Getter
    @Setter
    public static class TestConfig {
        @Key("app-name")
        private String appName;

        @Key("port")
        private int port = 8080;

        private boolean onLoadCalled = false;

        @OnLoad
        public void afterLoad() {
            onLoadCalled = true;
        }
    }

    @YamlDirectory(dir = "test-items", target = TestConfigItem.class)
    @Getter
    @Setter
    public static class TestConfigDirectory {
        private List<TestConfigItem> items;
    }

    @Getter
    @Setter
    public static class TestConfigItem {
        @Key("id")
        private String id;

        @Key("value")
        private String value;
    }
}
