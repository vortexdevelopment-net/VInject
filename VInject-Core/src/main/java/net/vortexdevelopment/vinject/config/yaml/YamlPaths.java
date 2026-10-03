package net.vortexdevelopment.vinject.config.yaml;

import java.util.ArrayList;
import java.util.List;

/**
 * Dot-path encoding for YAML config trees. Segments may contain literal dots when escaped ({@code \\.})
 * or when set as a single map key via {@link YamlConfig#setMapEntry}.
 */
public final class YamlPaths {

    private YamlPaths() {}

    /**
     * Splits a configuration path on unescaped {@code .} characters.
     */
    public static List<String> split(String path) {
        List<String> parts = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (escape) {
                current.append(c);
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '.') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escape) {
            current.append('\\');
        }
        parts.add(current.toString());
        return parts;
    }

    public static String[] splitToArray(String path) {
        List<String> parts = split(path);
        return parts.toArray(String[]::new);
    }

    public static String join(Iterable<String> segments) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String segment : segments) {
            if (!first) {
                sb.append('.');
            }
            sb.append(escapeSegment(segment));
            first = false;
        }
        return sb.toString();
    }

    public static String escapeSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment == null ? "" : segment;
        }
        if (!segment.contains("\\") && !segment.contains(".")) {
            return segment;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '\\' || c == '.') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Renders a mapping key for YAML output. Keys containing dots are wrapped in single quotes.
     */
    public static String formatMappingKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (needsQuoting(key)) {
            return "'" + key.replace("'", "''") + "'";
        }
        return key;
    }

    public static boolean needsQuoting(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        if (key.contains(".") || key.contains(":") || key.contains("#")) {
            return true;
        }
        return key.startsWith(" ") || key.endsWith(" ")
                || key.startsWith("\t") || key.endsWith("\t");
    }
}
