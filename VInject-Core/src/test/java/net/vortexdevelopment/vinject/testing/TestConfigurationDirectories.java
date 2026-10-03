package net.vortexdevelopment.vinject.testing;

import net.vortexdevelopment.vinject.di.ConfigurationContainer;

import java.nio.file.Path;

/**
 * Keeps {@link YamlConfiguration} integration tests from writing config files into the module root.
 */
public final class TestConfigurationDirectories {

    private TestConfigurationDirectories() {}

    public static void useTemporaryRoot(Path tempDir) {
        ConfigurationContainer.setRootDirectory(tempDir);
    }

    public static void resetRoot() {
        ConfigurationContainer.setRootDirectory((Path) null);
    }
}
