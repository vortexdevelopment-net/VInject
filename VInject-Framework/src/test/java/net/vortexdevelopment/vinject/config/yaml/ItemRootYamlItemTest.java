package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.yaml.ItemRoot;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.ConfigurationSection;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ItemRootYamlItemTest {

    @TempDir
    Path tempDir;

    @Root(packageName = "net.vortexdevelopment.vinject.config.yaml", createInstance = false)
    public static class TestRoot {}

    @YamlDirectory(dir = "itemroot-items", target = StateButton.class, copyDefaults = false, recursive = false)
    public static class ItemHolder {
        @YamlCollection
        private Map<String, StateButton> items = new HashMap<>();

        public Map<String, StateButton> getItems() {
            return items;
        }
    }

    @YamlItem
    public static class StateButton {
        @YamlId
        private String id;

        @Key("A")
        private boolean flagA;

        @ItemRoot
        private ConfigurationSection itemSection;

        @SuppressWarnings("unused")
        private String __vinject_yaml_batch_id;

        @SuppressWarnings("unused")
        private String __vinject_yaml_file;

        public String getId() {
            return id;
        }

        public boolean isFlagA() {
            return flagA;
        }

        public ConfigurationSection getItemSection() {
            return itemSection;
        }
    }

    @Test
    public void itemRootReflectsYamlAndPersistsApiOnlyKeys() throws Exception {
        ConfigurationContainer.setRootDirectory(tempDir);
        ConfigurationContainer.setForceSyncSave(true);

        Path batchDir = tempDir.resolve("itemroot-items");
        Files.createDirectories(batchDir);
        Path yml = batchDir.resolve("data.yml");
        Files.writeString(yml, "some_item:\n  A: true\n");

        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(ItemHolder.class)
                .build()) {

            ConfigurationContainer configContainer = ConfigurationContainer.getInstance();
            String batchId = ItemHolder.class.getName() + "::itemroot-items";

            StateButton item = (StateButton) configContainer.getBatch(batchId).get("some_item");
            assertNotNull(item);
            assertTrue(item.isFlagA(), "mapped field A");
            assertNotNull(item.getItemSection());
            assertTrue(item.getItemSection().getBoolean("A"), "ItemRoot sees A");

            item.getItemSection().set("B", false);
            configContainer.saveItemObject(item);

            String content = Files.readString(yml);
            assertTrue(content.contains("B"), "key added via ItemRoot should be saved: " + content);
            assertTrue(content.contains("A"), "original key A should remain: " + content);
        }
    }
}
