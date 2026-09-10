package net.vortexdevelopment.vinject.database.meta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class SchemaNameUtils {
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    private SchemaNameUtils() {
    }

    static String foreignKeyName(String table, String column, String referencedTable, String referencedColumn) {
        return normalize("fk_" + table + "_" + column + "_" + referencedTable + "_" + referencedColumn);
    }

    static String indexName(String table, Iterable<String> columns, boolean unique) {
        StringBuilder name = new StringBuilder(unique ? "uidx_" : "idx_").append(table);
        for (String column : columns) {
            name.append('_').append(column);
        }
        return normalize(name.toString());
    }

    static String normalize(String value) {
        String normalized = value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9_]+", "_");
        if (normalized.length() <= MAX_IDENTIFIER_LENGTH) {
            return normalized;
        }
        String hash = shortHash(normalized);
        return normalized.substring(0, MAX_IDENTIFIER_LENGTH - hash.length() - 1) + "_" + hash;
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
