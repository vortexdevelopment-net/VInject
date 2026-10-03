package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;

import java.util.List;

@Getter
public final class IndexMetadata {
    private final String name;
    private final List<String> columns;
    private final boolean unique;

    public IndexMetadata(String name, List<String> columns, boolean unique) {
        this.name = name;
        this.columns = List.copyOf(columns);
        this.unique = unique;
    }
}
