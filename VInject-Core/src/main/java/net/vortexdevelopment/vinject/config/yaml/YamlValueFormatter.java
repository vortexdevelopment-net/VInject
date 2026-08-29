package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.YamlSerializationWarnings;

import java.util.ArrayList;
import java.util.List;

public class YamlValueFormatter {

    public static String serialize(Object val) {
        return serialize(val, null);
    }

    public static String serialize(Object val, String contextPath) {
        if (val == null) {
            return "~";
        }
        if (val instanceof String s) {
            return "\"" + s.replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
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

        // YAML flow sequences (for example, [1, 2, 3]) are still sequences even
        // when their elements are unquoted. Parse each element using the same
        // scalar rules as block-list items.
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return deserializeInlineSequence(trimmed.substring(1, trimmed.length() - 1));
        }

        // Try booleans
        if (trimmed.equalsIgnoreCase("true")) return true;
        if (trimmed.equalsIgnoreCase("false")) return false;

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

    private static List<Object> deserializeInlineSequence(String content) {
        List<Object> result = new ArrayList<>();
        if (content.trim().isEmpty()) {
            return result;
        }

        for (String item : splitInlineSequence(content)) {
            result.add(deserialize(item));
        }
        return result;
    }

    private static List<String> splitInlineSequence(String content) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        int nesting = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\' && quote == '"') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }

            if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else if (c == '[' || c == '{') {
                nesting++;
                current.append(c);
            } else if (c == ']' || c == '}') {
                nesting--;
                current.append(c);
            } else if (c == ',' && nesting == 0) {
                items.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        items.add(current.toString().trim());
        return items;
    }
}
