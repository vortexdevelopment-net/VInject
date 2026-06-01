package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.YamlSerializationWarnings;

import java.util.ArrayList;

public class YamlValueFormatter {

    /**
     * Detects the mistaken quoted-string form {@code "[]"} (not a valid YAML list).
     */
    public static boolean isQuotedEmptyListString(Object value) {
        return value instanceof String string && isEmptyListLiteral(string);
    }

    /**
     * Unquoted {@code []} inline scalar (valid empty YAML list syntax).
     */
    public static boolean isEmptyListLiteral(String value) {
        return value != null && "[]".equals(value.trim());
    }

    public static String serialize(Object val) {
        return serialize(val, null);
    }

    public static String serialize(Object val, String contextPath) {
        return serialize(val, contextPath, -1, 2);
    }

    public static String serialize(Object val, String contextPath, int keyIndent, int indentStep) {
        if (val == null) {
            return "~";
        }
        if (val instanceof String s) {
            if (s.contains("\n") && keyIndent >= 0) {
                return serializeLiteralBlock(s, keyIndent, indentStep);
            }
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        YamlSerializationWarnings.warnIfToStringScalar(val, contextPath);
        return val.toString();
    }

    public static Object deserialize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.equals("~")) {
            return null;
        }
        
        if (trimmed.startsWith("\"")) {
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = 1; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (escaped) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 'r') sb.append('\r');
                    else if (c == 't') sb.append('\t');
                    else sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        if (trimmed.startsWith("'")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '\'' && i + 1 < trimmed.length() && trimmed.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    i++;
                } else if (c == '\'') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        // Try booleans
        if (trimmed.equalsIgnoreCase("true")) return true;
        if (trimmed.equalsIgnoreCase("false")) return false;

        if (isEmptyListLiteral(trimmed)) {
            return new ArrayList<>();
        }

        // Try numbers
        try {
            if (trimmed.contains(".")) {
                return Double.parseDouble(trimmed);
            } else {
                long val = Long.parseLong(trimmed);
                if (val <= Integer.MAX_VALUE && val >= Integer.MIN_VALUE) {
                    return (int) val;
                }
                return val;
            }
        } catch (NumberFormatException ignored) {
        }

        return trimmed;
    }

    /**
     * Renders a multiline string as a YAML literal block ({@code |}) with content indented by {@code indentStep}
     * relative to the key line.
     */
    public static String serializeLiteralBlock(String value, int keyIndent, int indentStep) {
        int step = indentStep > 0 ? indentStep : 2;
        int contentIndent = keyIndent + step;
        String contentPrefix = " ".repeat(contentIndent);
        StringBuilder out = new StringBuilder("|\n");
        if (value.isEmpty()) {
            return "|";
        }
        String[] lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.append(contentPrefix).append(lines[i]);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
