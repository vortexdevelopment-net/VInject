package net.vortexdevelopment.vinject.config.yaml;

import net.vortexdevelopment.vinject.config.YamlSerializationWarnings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between YAML scalar text and Java values, and renders Java values back to YAML.
 *
 * <p>Scalar resolution follows the YAML 1.1 core schema, which is what Bukkit's SnakeYAML
 * based configuration reader uses: {@code true/false}, {@code yes/no}, {@code on/off},
 * {@code ~}/{@code null} and decimal, hexadecimal, octal, underscored and exponent numbers
 * all resolve to their typed values. Quoted scalars always stay strings, so a value that must
 * keep its literal text only has to be quoted.</p>
 */
public class YamlValueFormatter {

    private static final Pattern INTEGER_PATTERN = Pattern.compile(
            "[-+]?(?:0b[0-1_]+|0x[0-9a-fA-F_]+|0o[0-7_]+|0[0-7_]+|[0-9][0-9_]*)");
    private static final Pattern FLOAT_PATTERN = Pattern.compile(
            "[-+]?(?:[0-9][0-9_]*\\.[0-9_]*|\\.[0-9][0-9_]*|[0-9][0-9_]*)(?:[eE][-+]?[0-9]+)?");
    private static final Pattern INFINITY_PATTERN = Pattern.compile("[-+]?\\.(?:inf|Inf|INF)");
    private static final Pattern NAN_PATTERN = Pattern.compile("\\.(?:nan|NaN|NAN)");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile(
            "(?:true|True|TRUE|false|False|FALSE|yes|Yes|YES|no|No|NO|on|On|ON|off|Off|OFF)");

    public static String serialize(Object val) {
        return serialize(val, null);
    }

    public static String serialize(Object val, String contextPath) {
        if (val == null) {
            return "~";
        }
        if (val instanceof String s) {
            return quote(s);
        }
        if (val instanceof Map<?, ?> map) {
            return serializeInlineMapping(map, contextPath);
        }
        if (val instanceof List<?> list) {
            return serializeInlineSequence(list, contextPath);
        }
        YamlSerializationWarnings.warnIfToStringScalar(val, contextPath);
        return val.toString();
    }

    public static String serialize(Object val, String contextPath, int keyIndent, int indentStep) {
        if (val instanceof String string && string.contains("\n") && keyIndent >= 0) {
            return serializeLiteralBlock(string, keyIndent, indentStep);
        }
        return serialize(val, contextPath);
    }

    public static String serializeLiteralBlock(String value, int keyIndent, int indentStep) {
        int step = indentStep > 0 ? indentStep : 2;
        String contentPrefix = " ".repeat(keyIndent + step);
        StringBuilder output = new StringBuilder("|\n");
        if (value.isEmpty()) {
            return "|";
        }
        String[] lines = value.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            output.append(contentPrefix).append(lines[i]);
            if (i < lines.length - 1) {
                output.append('\n');
            }
        }
        return output.toString();
    }

    /**
     * Renders a string as a single line YAML double quoted scalar.
     *
     * @param value The text to quote
     * @return The quoted scalar
     */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\0' -> sb.append("\\0");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String serializeInlineSequence(List<?> list, String contextPath) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(serialize(list.get(i), contextPath));
        }
        return sb.append(']').toString();
    }

    private static String serializeInlineMapping(Map<?, ?> map, String contextPath) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey() == null ? "~" : String.valueOf(entry.getKey())).append(": ")
                    .append(serialize(entry.getValue(), contextPath));
            first = false;
        }
        return sb.append('}').toString();
    }

    /**
     * Resolves unquoted or quoted scalar text into a typed value.
     *
     * @param value The scalar text
     * @return The resolved value, which may be null, a Boolean, a Number, a String, a List or a Map
     */
    public static Object deserialize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return value;
        }

        if (trimmed.startsWith("\"")) {
            return unescapeDoubleQuoted(trimmed);
        }

        if (trimmed.startsWith("'")) {
            return unescapeSingleQuoted(trimmed);
        }

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return deserializeInlineSequence(trimmed.substring(1, trimmed.length() - 1));
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return deserializeInlineMapping(trimmed.substring(1, trimmed.length() - 1));
        }

        if (trimmed.equals("~") || isNullLiteral(trimmed)) {
            return null;
        }

        Object typed = resolveImplicit(trimmed);
        return typed != null ? typed : trimmed;
    }

    public static boolean isQuotedEmptyListString(Object value) {
        return value instanceof String string && "[]".equals(string.trim());
    }

    /**
     * Resolves the implicit type of an unquoted scalar without falling back to a string.
     *
     * @param text The trimmed scalar text
     * @return The typed value, or null when the text is not a core schema literal
     */
    static Object resolveImplicit(String text) {
        if (BOOLEAN_PATTERN.matcher(text).matches()) {
            return resolveBoolean(text);
        }
        Object integer = resolveInteger(text);
        if (integer != null) {
            return integer;
        }
        return resolveFloat(text);
    }

    static boolean isNullLiteral(String text) {
        return text.equals("null") || text.equals("Null") || text.equals("NULL");
    }

    static boolean isBooleanLiteral(String text) {
        return BOOLEAN_PATTERN.matcher(text).matches();
    }

    static Boolean resolveBoolean(String text) {
        return switch (text) {
            case "true", "True", "TRUE", "yes", "Yes", "YES", "on", "On", "ON" -> Boolean.TRUE;
            case "false", "False", "FALSE", "no", "No", "NO", "off", "Off", "OFF" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * Resolves a YAML 1.1 integer, including binary, hexadecimal, octal and underscored forms.
     *
     * @param text The trimmed scalar text
     * @return The integer value, or null when the text is not an integer
     */
    static Object resolveInteger(String text) {
        if (!INTEGER_PATTERN.matcher(text).matches()) {
            return null;
        }

        String normalized = text.replace("_", "");
        boolean negative = normalized.startsWith("-");
        boolean positive = normalized.startsWith("+");
        String digits = (negative || positive) ? normalized.substring(1) : normalized;

        int radix = 10;
        if (digits.startsWith("0b")) {
            radix = 2;
            digits = digits.substring(2);
        } else if (digits.startsWith("0x")) {
            radix = 16;
            digits = digits.substring(2);
        } else if (digits.startsWith("0o")) {
            radix = 8;
            digits = digits.substring(2);
        } else if (digits.length() > 1 && digits.startsWith("0")) {
            radix = 8;
            digits = digits.substring(1);
        }

        try {
            long parsed = Long.parseLong(digits, radix);
            if (negative) {
                parsed = -parsed;
            }
            if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                return (int) parsed;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Resolves a YAML 1.1 float, including exponent, infinity and not-a-number forms.
     *
     * @param text The trimmed scalar text
     * @return The double value, or null when the text is not a float
     */
    static Double resolveFloat(String text) {
        if (NAN_PATTERN.matcher(text).matches()) {
            return Double.NaN;
        }
        if (INFINITY_PATTERN.matcher(text).matches()) {
            return text.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (!FLOAT_PATTERN.matcher(text).matches() || !text.contains(".") && !text.contains("e") && !text.contains("E")) {
            return null;
        }
        try {
            return Double.parseDouble(text.replace("_", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Unescapes a YAML double quoted scalar, including its closing quote.
     *
     * @param text The scalar text starting with a double quote
     * @return The unescaped content
     */
    static String unescapeDoubleQuoted(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }

            i = appendEscapeSequence(sb, text, i);
            if (i < 0) {
                throw new IllegalArgumentException("Unsupported YAML syntax: unterminated escape sequence in double quoted scalar: " + text);
            }
        }
        return sb.toString();
    }

    /**
     * Appends the character produced by the escape sequence at {@code index} and returns the
     * index of the last consumed character.
     */
    private static int appendEscapeSequence(StringBuilder sb, String text, int index) {
        if (index + 1 >= text.length()) {
            return -1;
        }
        char escape = text.charAt(index + 1);
        switch (escape) {
            case '0' -> sb.append('\0');
            case 'a' -> sb.append('\u0007');
            case 'b' -> sb.append('\b');
            case 't' -> sb.append('\t');
            case 'n' -> sb.append('\n');
            case 'v' -> sb.append('\u000B');
            case 'f' -> sb.append('\f');
            case 'r' -> sb.append('\r');
            case 'e' -> sb.append('\u001B');
            case ' ' -> sb.append(' ');
            case '"' -> sb.append('"');
            case '/' -> sb.append('/');
            case '\\' -> sb.append('\\');
            case 'N' -> sb.append('\u0085');
            case '_' -> sb.append('\u00A0');
            case 'L' -> sb.append('\u2028');
            case 'P' -> sb.append('\u2029');
            case 'x' -> {
                return appendCodePoint(sb, text, index + 2, 2, escape);
            }
            case 'u' -> {
                return appendCodePoint(sb, text, index + 2, 4, escape);
            }
            case 'U' -> {
                return appendCodePoint(sb, text, index + 2, 8, escape);
            }
            default -> throw new IllegalArgumentException("Unsupported YAML escape sequence '\\" + escape
                    + "' in double quoted scalar: " + text);
        }
        return index + 1;
    }

    private static int appendCodePoint(StringBuilder sb, String text, int start, int length, char escape) {
        if (start + length > text.length()) {
            throw new IllegalArgumentException("Unsupported YAML escape sequence '\\" + escape
                    + "' in double quoted scalar: " + text);
        }
        String hex = text.substring(start, start + length);
        try {
            sb.appendCodePoint(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported YAML escape sequence '\\" + escape + hex
                    + "' in double quoted scalar: " + text);
        }
        return start + length - 1;
    }

    /**
     * Unescapes a YAML single quoted scalar, including its closing quote.
     *
     * @param text The scalar text starting with a single quote
     * @return The unescaped content
     */
    static String unescapeSingleQuoted(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    sb.append('\'');
                    i++;
                    continue;
                }
                return sb.toString();
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Parses the contents of a YAML flow sequence.
     *
     * @param content The text between the enclosing brackets
     * @return The parsed list
     */
    static List<Object> deserializeInlineSequence(String content) {
        List<Object> result = new ArrayList<>();
        if (content.trim().isEmpty()) {
            return result;
        }

        for (String item : splitFlow(content)) {
            result.add(deserialize(item));
        }
        return result;
    }

    /**
     * Parses the contents of a YAML flow mapping.
     *
     * @param content The text between the enclosing braces
     * @return The parsed mapping in document order
     */
    static Map<String, Object> deserializeInlineMapping(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (content.trim().isEmpty()) {
            return result;
        }

        for (String entry : splitFlow(content)) {
            int separator = findFlowSeparator(entry);
            if (separator < 0) {
                throw new IllegalArgumentException("Unsupported YAML syntax: flow mapping entry without a key: '" + entry + "'");
            }
            String key = unquoteKey(entry.substring(0, separator).trim());
            String rawValue = entry.substring(separator + 1);
            result.put(key, deserialize(rawValue));
        }
        return result;
    }

    private static String unquoteKey(String key) {
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            return unescapeDoubleQuoted(key);
        }
        if (key.length() >= 2 && key.startsWith("'") && key.endsWith("'")) {
            return unescapeSingleQuoted(key);
        }
        return YamlParser.normalizeMappingKey(key);
    }

    private static int findFlowSeparator(String entry) {
        char quote = 0;
        boolean escaped = false;
        int nesting = 0;
        for (int i = 0; i < entry.length(); i++) {
            char c = entry.charAt(i);
            if (quote != 0) {
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
            } else if (c == '[' || c == '{') {
                nesting++;
            } else if (c == ']' || c == '}') {
                nesting--;
            } else if (c == ':' && nesting == 0 && i + 1 < entry.length() && Character.isWhitespace(entry.charAt(i + 1))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Splits flow collection content on commas that are not nested or quoted.
     *
     * @param content The flow content
     * @return The individual entries
     */
    private static List<String> splitFlow(String content) {
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
