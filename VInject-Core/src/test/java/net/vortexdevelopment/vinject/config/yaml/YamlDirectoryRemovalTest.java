package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.yaml.YamlCollection;
import net.vortexdevelopment.vinject.annotation.yaml.YamlDirectory;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.di.ConfigurationContainer;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class YamlDirectoryRemovalTest {

    @TempDir
    Path tempDir;

    @Root(packageName = "net.vortexdevelopment.vinject.config.yaml", createInstance = false)
    public static class TestRoot {}

    @YamlDirectory(dir = "vouchers", target = VoucherEntry.class)
    public static class VoucherDirectory {
        @YamlCollection
        private Map<String, VoucherEntry> vouchers = new HashMap<>();

        public Map<String, VoucherEntry> getVouchers() {
            return vouchers;
        }
    }

    public static class VoucherEntry {
        @YamlId
        private String id;
        private String data = "some data";

        // Synthetic fields added by transformer in real app
        private String __vinject_yaml_batch_id;
        private String __vinject_yaml_file;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @Test
    public void testRemoveItemAndSave() {
        ConfigurationContainer.setRootDirectory(tempDir);
        ConfigurationContainer.setForceSyncSave(true);
        
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(TestRoot.class)
                .withComponents(VoucherDirectory.class)
                .build()) {
            
            ConfigurationContainer configContainer = ConfigurationContainer.getInstance();
            VoucherDirectory holder = context.getComponent(VoucherDirectory.class);
            
            VoucherEntry entry = new VoucherEntry();
            entry.setId("test-voucher");
            
            // 1. Register and save item
            configContainer.registerAndSaveItemObject(VoucherDirectory.class, "test-voucher.yml", entry);
            
            File file = tempDir.resolve("vouchers/test-voucher.yml").toFile();
            assertTrue(file.exists(), "File should be created");
            
            // 2. Remove item from memory map (as the user did)
            holder.getVouchers().remove("test-voucher");
            
            // 3. Save config for the directory class
            configContainer.saveConfig(VoucherDirectory.class, true);
            
            // 4. Check if file is gone
            assertFalse(file.exists(), "File should be deleted after removing item from map and saving config");
        }
    }
}
