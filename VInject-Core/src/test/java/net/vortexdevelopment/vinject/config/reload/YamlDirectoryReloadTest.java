package net.vortexdevelopment.vinject.config.reload;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.lifecycle.OnLoad;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDirectoryReloadTest {

    @TempDir
    Path tempDir;

    @Root(packageName = "net.vortexdevelopment.vinject.config.reload", createInstance = false)
    static class TestRoot {
    }

    @YamlDirectory(dir = "kits", target = Kit.class, copyDefaults = false)
    static class Kits {
        @YamlCollection
        private final Map<String, Kit> items = new LinkedHashMap<>();
        @YamlCollection
        private final List<Kit> list = new ArrayList<>();
        private transient int loads;

        @OnLoad
        void loaded() {
            loads++;
            for (Kit kit : items.values()) {
                assertEquals(1, kit.loads);
            }
        }
    }

    @YamlDirectory(dir = "grouped", rootKey = "Kits", target = Kit.class, copyDefaults = false)
    static class GroupedKits {
        @YamlCollection
        private final Map<String, Kit> items = new LinkedHashMap<>();
    }

    @YamlItem
    static class Kit {
        @YamlId
        private String id;
        @Key("Name")
        private String name;
        @Key("Commands")
        private List<String> commands;
        private transient int loads;

        @OnLoad
        void loaded() {
            loads++;
        }
    }

    private TestApplicationContext context() {
        ConfigurationContainer.setRootDirectory(tempDir);
        return TestApplicationContext.builder().withRootClass(TestRoot.class)
                .withComponents(Kits.class, GroupedKits.class).build();
    }

    private Path write(String file, String yaml) throws Exception {
        Path path = tempDir.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, yaml);
        return path;
    }

    @Test
    void rereadsEditedFilesAddsNewFilesAndRemovesDeletedFilesWithoutReplacingHolderCollections() throws Exception {
        write("kits/basic.yml", "Id: ultimatekits:basic\nName: Basic\nCommands:\n  - \"broadcast Hi: all\"\n");
        Path removed = write("kits/removed.yaml", "Id: removed\nName: Removed\n");
        try (TestApplicationContext context = context()) {
            Kits holder = context.getComponent(Kits.class);
            Map<String, Kit> map = holder.items;
            List<Kit> list = holder.list;
            ConfigurationContainer configs = context.getComponent(ConfigurationContainer.class);
            Map<String, Object> batch = configs.getBatch(Kits.class.getName() + "::kits");
            int initialLoads = holder.loads;

            write("kits/basic.yml", "Id: ultimatekits:basic\nName: Edited\nCommands:\n  - \"broadcast Price: $100\"\n");
            write("kits/nested/added.yml", "Id: added\nName: Added\n");
            Files.delete(removed);
            configs.reloadConfig(Kits.class);

            assertSame(map, holder.items);
            assertSame(list, holder.list);
            assertEquals(Set.of("ultimatekits:basic", "added"), map.keySet());
            assertEquals(map.keySet(), batch.keySet());
            assertEquals("Edited", map.get("ultimatekits:basic").name);
            assertEquals(List.of("broadcast Price: $100"), map.get("ultimatekits:basic").commands);
            assertEquals(2, list.size());
            assertEquals(initialLoads + 1, holder.loads);

            configs.reloadConfig(Kits.class);
            assertEquals(2, list.size());
            assertEquals(1, map.get("added").loads);
        }
    }

    @Test
    void removesDeletedGroupedEntriesUnderRootKey() throws Exception {
        write("grouped/nested/sets.yml", "Kits:\n  first:\n    Name: First\n  second:\n    Name: Second\n");
        try (TestApplicationContext context = context()) {
            GroupedKits holder = context.getComponent(GroupedKits.class);
            write("grouped/nested/sets.yml", "Kits:\n  second:\n    Name: Edited\n");
            context.getComponent(ConfigurationContainer.class).reloadConfig(GroupedKits.class);
            assertEquals(Set.of("second"), holder.items.keySet());
            assertEquals("Edited", holder.items.get("second").name);
        }
    }

    @Test
    void emptiesBatchWhenDirectoryIsRemoved() throws Exception {
        Path file = write("kits/basic.yml", "Name: Basic\n");
        try (TestApplicationContext context = context()) {
            Files.delete(file);
            Files.delete(file.getParent());
            ConfigurationContainer configs = context.getComponent(ConfigurationContainer.class);
            configs.reloadConfig(Kits.class);
            Kits holder = context.getComponent(Kits.class);
            assertTrue(holder.items.isEmpty());
            assertTrue(holder.list.isEmpty());
            assertTrue(configs.getBatch(Kits.class.getName() + "::kits").isEmpty());
            assertTrue(Files.notExists(file.getParent()));
        }
    }

    @Test
    void failedReloadPreservesPreviouslyLoadedBatchAndIncludesFilePath() throws Exception {
        write("kits/basic.yml", "Name: Basic\n");
        try (TestApplicationContext context = context()) {
            Kits holder = context.getComponent(Kits.class);
            Kit original = holder.items.get("basic");
            write("kits/basic.yml", "Name: Edited\n");
            Path invalid = write("kits/invalid.yml", "Name: \"unterminated\n");
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> context.getComponent(ConfigurationContainer.class).reloadConfig(Kits.class));
            assertTrue(failure.getMessage().contains(invalid.toAbsolutePath().toString()));
            assertEquals(Set.of("basic"), holder.items.keySet());
            assertSame(original, holder.items.get("basic"));
            assertEquals("Basic", original.name);
        }
    }
}
