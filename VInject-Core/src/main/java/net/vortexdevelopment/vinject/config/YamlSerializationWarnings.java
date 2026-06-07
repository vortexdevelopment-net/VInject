package net.vortexdevelopment.vinject.config;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects values that will be written as a scalar via {@link Object#toString()} instead of
 * structured YAML (reflection, {@link net.vortexdevelopment.vinject.annotation.yaml.YamlItem}, or a custom serializer).
 */
public final class YamlSerializationWarnings {

    private static final Set<String> WARNED_TYPES = ConcurrentHashMap.newKeySet();

    private YamlSerializationWarnings() {
    }

    /**
     * True when {@code value.toString()} matches the default {@link Object#toString()} identity format
     * ({@code com.example.Foo@1a2b3c4d}).
     */
    public static boolean isDefaultObjectToString(Object value) {
        if (value == null) {
            return false;
        }
        String rendered = value.toString();
        String prefix = value.getClass().getName() + '@';
        if (!rendered.startsWith(prefix)) {
            return false;
        }
        String suffix = rendered.substring(prefix.length());
        return !suffix.isEmpty() && suffix.matches("[0-9a-fA-F]+");
    }

    /**
     * Logs a one-time warning per concrete class when a non-scalar value is serialized through {@code toString()}.
     *
     * @param value   the value about to be written as a scalar
     * @param context optional path or key for the message (may be null)
     */
    public static void warnIfToStringScalar(Object value, String context) {
        if (value == null || isSafeScalar(value)) {
            return;
        }
        Class<?> clazz = value.getClass();
        if (!WARNED_TYPES.add(clazz.getName())) {
            return;
        }

        String where = (context == null || context.isEmpty()) ? "" : " at '" + context + "'";
        if (isDefaultObjectToString(value)) {
            System.err.println("[VInject YAML] Value" + where + " uses default Object.toString() for type "
                    + clazz.getName() + " (" + value + "). "
                    + "Add @YamlItem, register a YamlSerializer, or use a type with mappable instance fields.");
            return;
        }

        if (!YamlReflectiveMapping.supportsFieldReflection(clazz)
                && !clazz.isEnum()
                && !clazz.isArray()) {
            System.err.println("[VInject YAML] Value" + where + " is serialized via toString() for type "
                    + clazz.getName() + " (\"" + truncate(value.toString(), 80) + "\"). "
                    + "This may not round-trip. Prefer @YamlItem, a YamlSerializer, or mappable fields.");
        }
    }

    static void clearWarningsForTests() {
        WARNED_TYPES.clear();
    }

    private static boolean isSafeScalar(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value.getClass().isEnum();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }
}
