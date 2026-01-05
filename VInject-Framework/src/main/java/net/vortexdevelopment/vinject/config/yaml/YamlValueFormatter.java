package net.vortexdevelopment.vinject.config.yaml;

public class YamlValueFormatter {

    public static String serialize(Object val) {
        if (val == null) {
            return "~";
        }
        if (val instanceof String s) {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
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
}
