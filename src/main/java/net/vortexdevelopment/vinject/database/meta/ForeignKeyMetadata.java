package net.vortexdevelopment.vinject.database.meta;

import lombok.Getter;

@Getter
public class ForeignKeyMetadata {
    private final String referencedTable;
    private final String referencedColumn;

    public ForeignKeyMetadata(String referencedTable, String referencedColumn) {
        this.referencedTable = referencedTable;
        this.referencedColumn = referencedColumn;
    }
}
