package net.vortexdevelopment.vinject.config;

import lombok.Getter;
import lombok.Setter;
import net.vortexdevelopment.vinject.annotation.Inject;
import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigurationClasspathDefaultTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultResourceIsCopiedWhenFileDoesNotExist() throws Exception {
        // Arrange
        ConfigurationContainer.setRootDirectory(tempDir);
        Path targetFile = tempDir.resolve("test-classpath-defaults.yml");
        assertThat(Files.exists(targetFile)).isFalse();

        // Act
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .build()) {

            // Get the loaded config class instance
            ClasspathDefaultConfig config = context.getComponent(ClasspathDefaultConfig.class);

            // Assert: Config fields should have values loaded from the classpath default
            assertThat(config).isNotNull();
            assertThat(config.getAppName()).isEqualTo("Classpath Default App");
            assertThat(config.getPort()).isEqualTo(9090);

            // Assert: The file should now exist on disk and have the same content
            assertThat(Files.exists(targetFile)).isTrue();
            String fileContent = Files.readString(targetFile);
            assertThat(fileContent).contains("app-name: \"Classpath Default App\"");
            assertThat(fileContent).contains("port: 9090");
        } finally {
            // Reset root directory to avoid polluting other tests
            ConfigurationContainer.setRootDirectory((Path) null);
        }
    }

    @Root(packageName = "net.vortexdevelopment.vinject.config", createInstance = false)
    static class TestRoot {
    }

    @YamlConfiguration(file = "test-classpath-defaults.yml")
    @Getter
    @Setter
    public static class ClasspathDefaultConfig {
        @Key("app-name")
        private String appName;

        @Key("port")
        private int port;
    }
}
