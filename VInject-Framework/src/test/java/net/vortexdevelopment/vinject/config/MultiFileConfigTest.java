package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlConfiguration;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.di.scan.ClasspathScanner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class MultiFileConfigTest {

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
        }
        file.delete();
    }

    @Root
    public static class TestApp {}

    @Test
    public void testMultipleConfigsSameFile() throws Exception {
        Path tempDir = Files.createTempDirectory("vinject-test");
        ConfigurationContainer.setRootDirectory(tempDir);
        try {
            // Setup DependencyContainer
            Class<?> rootClass = TestApp.class;
            Root rootAnnotation = rootClass.getAnnotation(Root.class);
            DependencyContainer container = new DependencyContainer(rootAnnotation, rootClass, new TestApp(), null, null, null);
            
            ClasspathScanner scanner = new ClasspathScanner(rootAnnotation, rootClass);
            Reflections reflections = new Reflections("net.vortexdevelopment.vinject.config");

            Set<Class<?>> configClasses = new HashSet<>();
            configClasses.add(ModuleBlocksConfig.class);
            configClasses.add(ModuleItemsConfig.class);

            // Initialize ConfigurationContainer which should load and merge these configs
            new ConfigurationContainer(container, scanner, reflections, configClasses);

            // Check the file content
            File configFile = tempDir.resolve("shared-config.yml").toFile();
            Assertions.assertTrue(configFile.exists(), "Config file should be created");

            String content = Files.readString(configFile.toPath());
            System.out.println("Config Content:\n" + content);

            // Verify both sections exist
            Assertions.assertTrue(content.contains("Blocks:"), "Should contain Blocks section. Content: " + content);
            Assertions.assertTrue(content.contains("Items:"), "Should contain Items section");
            Assertions.assertTrue(content.contains("Enabled: true"), "Should contain value from Blocks. Actual content: \n" + content);
            Assertions.assertTrue(content.contains("MaxItems: 64"), "Should contain value from Items");
            
            // Also verify that we can read back correct values through the container/mapper
            // (Simulating how the application would access it)
            ModuleBlocksConfig blocksConfig = (ModuleBlocksConfig) container.getDependencyOrNull(ModuleBlocksConfig.class);
            ModuleItemsConfig itemsConfig = (ModuleItemsConfig) container.getDependencyOrNull(ModuleItemsConfig.class);
            
            Assertions.assertNotNull(blocksConfig);
            Assertions.assertNotNull(itemsConfig);
            Assertions.assertTrue(blocksConfig.enableStacking);
            Assertions.assertEquals(64, itemsConfig.maxItems);
        } finally {
            deleteDirectory(tempDir.toFile());
            ConfigurationContainer.setRootDirectory((Path) null);
        }
    }

    @YamlConfiguration(file = "shared-config.yml")
    public static class ModuleBlocksConfig {
        @Key("Modules.Blocks.Enabled")
        boolean enableStacking = true;
    }

    @YamlConfiguration(file = "shared-config.yml")
    public static class ModuleItemsConfig {
        @Key("Modules.Items.MaxItems")
        int maxItems = 64;
    }
}
