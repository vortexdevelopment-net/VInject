package net.vortexdevelopment.vinject.di;

import java.nio.file.Path;

/**
 * Identifies the YAML file and batch entry that failed while mapping a configuration item.
 */
public final class YamlEntryLoadException extends IllegalStateException {

    public YamlEntryLoadException(Path file, String entryId, String detail, Throwable cause) {
        super("Invalid YAML entry '" + entryId + "' in " + file.toAbsolutePath() + ": " + detail, cause);
    }
}
