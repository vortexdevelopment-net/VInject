package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YamlDirectorySingleFileTest {

    @TempDir
    Path tempDir;

    @Root(packageName = "net.vortexdevelopment.vinject.config.yaml", createInstance = false)
    static class TestRoot {
    }

    @YamlDirectory(dir = "single-items", target = SingleItem.class, copyDefaults = false)
    static class SingleItemDirectory {
        @YamlCollection
        private Map<String, SingleItem> items = new LinkedHashMap<>();

        Map<String, SingleItem> getItems() {
            return items;
        }
    }

    @YamlItem
    static class SingleItem {
        @YamlId
        private String id;

        @Key("Name")
        private String name;

        String getId() {
            return id;
        }

        String getName() {
            return name;
        }
    }

    @Test
    void loadsDirectSingleItemFilesAndPreservesConfiguredNamespacedId() throws Exception {
        ConfigurationContainer.setRootDirectory(tempDir);
        Path directory = tempDir.resolve("single-items");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("normal.yml"), """
                Id: vortexskyblock:normal
                Name: Normal
                """);

        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(SingleItemDirectory.class)
                .build()) {
            SingleItem item = context.getComponent(SingleItemDirectory.class)
                    .getItems()
                    .get("vortexskyblock:normal");

            assertNotNull(item);
            assertEquals("vortexskyblock:normal", item.getId());
            assertEquals("Normal", item.getName());
        }
    }
}
