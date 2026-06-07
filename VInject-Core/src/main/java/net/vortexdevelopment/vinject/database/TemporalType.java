package net.vortexdevelopment.vinject.database;

public enum TemporalType {

    DATE,
    TIME,
    TIMESTAMP;

    private TemporalType() {
    }

    public static TemporalType fromString(String string) {
        return valueOf(string.toUpperCase());
    }
}
