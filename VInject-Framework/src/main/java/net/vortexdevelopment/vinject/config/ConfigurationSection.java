package net.vortexdevelopment.vinject.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for accessing and manipulating configuration sections.
 */
public interface ConfigurationSection {

    /**
     * Get the internal data-backed section. Default implementation returns this.
     * For DI-managed beans, this uses reflection to find the section registered
     * in the ConfigurationContainer.
     */
    default ConfigurationSection getConfigurationSection() {
        try {
            // Bridge to ConfigurationContainer for DI-managed beans
            Class<?> containerClass = Class.forName("net.vortexdevelopment.vinject.di.ConfigurationContainer");
            java.lang.reflect.Method getInstanceMethod = containerClass.getMethod("getInstance");
            Object container = getInstanceMethod.invoke(null);
            if (container != null) {
                java.lang.reflect.Method getSectionMethod = containerClass.getMethod("getSectionForInstance", Object.class);
                Object section = getSectionMethod.invoke(container, this);
                if (section instanceof ConfigurationSection) {
                    return (ConfigurationSection) section;
                }
            }
        } catch (Exception ignored) {
        }
        return this;
    }

    default <T> T get(String path) {
        if (getConfigurationSection() == this) return null;
        return getConfigurationSection().get(path);
    }

    default <T> T get(String path, Class<T> type) {
        if (getConfigurationSection() == this) return null;
        return getConfigurationSection().get(path, type);
    }

    default <T> T get(String path, T defaultValue) {
        if (getConfigurationSection() == this) return defaultValue;
        return getConfigurationSection().get(path, defaultValue);
    }

    default void set(String path, Object value) {
        set(path, value, null);
    }

    default void set(String path, Object value, String comment) {
        if (getConfigurationSection() != this) {
            getConfigurationSection().set(path, value, comment);
        }
    }

    default boolean contains(String path) {
        if (getConfigurationSection() == this) return false;
        return getConfigurationSection().contains(path);
    }

    default Set<String> getKeys(boolean deep) {
        if (getConfigurationSection() == this) return Collections.emptySet();
        return getConfigurationSection().getKeys(deep);
    }

    default ConfigurationSection getSection(String path) {
        if (getConfigurationSection() == this) return null;
        return getConfigurationSection().getSection(path);
    }

    default Map<String, Object> getValues(boolean deep) {
        if (getConfigurationSection() == this) return Collections.emptyMap();
        return getConfigurationSection().getValues(deep);
    }

    default ConfigurationSection getConfigurationSection(String path) {
        return getSection(path);
    }

    /**
     * Get or create a configuration section at the given path.
     * If the path does not exist, an empty section will be created.
     * @param path the path to the section
     * @return the section at the given path
     */
    default ConfigurationSection createSection(String path) {
        if (getConfigurationSection() == this) return null;
        return getConfigurationSection().createSection(path);
    }

    /**
     * Alias for {@link #createSection(String)}.
     */
    default ConfigurationSection create(String path) {
        return createSection(path);
    }

    default String getString(String path) {
        return get(path, String.class);
    }

    default String getString(String path, String def) {
        String val = getString(path);
        return val != null ? val : def;
    }

    default int getInt(String path) {
        return getInt(path, 0);
    }

    default int getInt(String path, int def) {
        Integer val = get(path, Integer.class);
        return val != null ? val : def;
    }

    default long getLong(String path) {
        return getLong(path, 0L);
    }

    default long getLong(String path, long def) {
        Long val = get(path, Long.class);
        return val != null ? val : def;
    }

    default double getDouble(String path) {
        return getDouble(path, 0.0);
    }

    default double getDouble(String path, double def) {
        Double val = get(path, Double.class);
        return val != null ? val : def;
    }

    default boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    default boolean getBoolean(String path, boolean def) {
        Boolean val = get(path, Boolean.class);
        return val != null ? val : def;
    }

    default List<?> getList(String path) {
        return get(path, List.class);
    }

    default List<?> getList(String path, List<?> defaultValue) {
        List<?> val = getList(path);
        return val != null ? val : defaultValue;
    }

    default List<String> getStringList(String path) {
        Object val = get(path);
        if (val instanceof List) {
            java.util.List<String> result = new java.util.ArrayList<>();
            for (Object obj : (List<?>) val) {
                if (obj != null) result.add(obj.toString());
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    default List<Integer> getIntegerList(String path) {
        Object val = get(path);
        if (val instanceof List) {
            java.util.List<Integer> result = new java.util.ArrayList<>();
            for (Object obj : (List<?>) val) {
                if (obj instanceof Number n) result.add(n.intValue());
                else if (obj != null) try { result.add(Integer.parseInt(obj.toString())); } catch (NumberFormatException ignored) {}
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    default List<Long> getLongList(String path) {
        Object val = get(path);
        if (val instanceof List) {
            java.util.List<Long> result = new java.util.ArrayList<>();
            for (Object obj : (List<?>) val) {
                if (obj instanceof Number n) result.add(n.longValue());
                else if (obj != null) try { result.add(Long.parseLong(obj.toString())); } catch (NumberFormatException ignored) {}
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    default List<Double> getDoubleList(String path) {
        Object val = get(path);
        if (val instanceof List) {
            java.util.List<Double> result = new java.util.ArrayList<>();
            for (Object obj : (List<?>) val) {
                if (obj instanceof Number n) result.add(n.doubleValue());
                else if (obj != null) try { result.add(Double.parseDouble(obj.toString())); } catch (NumberFormatException ignored) {}
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    default List<Boolean> getBooleanList(String path) {
        Object val = get(path);
        if (val instanceof List) {
            java.util.List<Boolean> result = new java.util.ArrayList<>();
            for (Object obj : (List<?>) val) {
                if (obj instanceof Boolean b) result.add(b);
                else if (obj != null) result.add(Boolean.parseBoolean(obj.toString()));
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }

    default boolean isString(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isString(path);
        return get(path) instanceof String;
    }

    default boolean isInt(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isInt(path);
        Object val = get(path);
        return val instanceof Integer;
    }

    default boolean isLong(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isLong(path);
        Object val = get(path);
        return val instanceof Long || val instanceof Integer;
    }

    default boolean isDouble(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isDouble(path);
        Object val = get(path);
        return val instanceof Double || val instanceof Float;
    }

    default boolean isBoolean(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isBoolean(path);
        return get(path) instanceof Boolean;
    }

    default boolean isList(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isList(path);
        return get(path) instanceof List;
    }

    default boolean isSection(String path) {
        if (getConfigurationSection() != this) return getConfigurationSection().isSection(path);
        Object val = get(path);
        return val instanceof java.util.Map || getSection(path) != null;
    }

    default void setObject(String path, Object value) {
        set(path, value);
    }

    default void setObject(Object value) {
        set("", value);
    }

    default void save() {
        if (getConfigurationSection() != this) {
            getConfigurationSection().save();
        }
    }

    default boolean isDirty() {
        if (getConfigurationSection() == this) return false;
        return getConfigurationSection().isDirty();
    }
}
