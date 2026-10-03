package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.database.ForeignKeyAction;

@Getter
public final class ForeignKeyMetadata {
    private final String name;
    private final String referencedTable;
    private final String referencedColumn;
    private final ForeignKeyAction onDelete;
    private final ForeignKeyAction onUpdate;

    public ForeignKeyMetadata(String name, String referencedTable, String referencedColumn,
                              ForeignKeyAction onDelete, ForeignKeyAction onUpdate) {
        this.name = name;
        this.referencedTable = referencedTable;
        this.referencedColumn = referencedColumn;
        this.onDelete = onDelete;
        this.onUpdate = onUpdate;
    }
}
