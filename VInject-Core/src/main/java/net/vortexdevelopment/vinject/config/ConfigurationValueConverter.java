package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.yaml.ItemRoot;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import net.vortexdevelopment.vinject.config.yaml.YamlValueFormatter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts raw YAML-backed values ({@link ConfigurationSection#get(String)}) to Java types,
 * including enums, collections, maps, serializers, and nested objects. Nested objects are
 * constructed via {@link ConfigObjectFactory} (DI vs plain reflection).
 */
public final class ConfigurationValueConverter {

    private final ConfigObjectFactory factory;

    public ConfigurationValueConverter(ConfigObjectFactory factory) {
        this.factory = factory;
    }

    private static final ThreadLocal<Boolean> MISSING_KEYS_TRACKER = new ThreadLocal<>();

    public boolean mapToInstance(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        boolean isTopLevel = (MISSING_KEYS_TRACKER.get() == null);
        if (isTopLevel) {
            MISSING_KEYS_TRACKER.set(false);
        }

        try {
            int itemRootCount = 0;
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                if (f.isAnnotationPresent(ItemRoot.class)) {
                    itemRootCount++;
                }
            }
            if (itemRootCount > 1) {
                throw new IllegalArgumentException("At most one @ItemRoot field is allowed on " + clazz.getName());
            }

            ConfigurationSection effectiveRoot = (basePath == null || basePath.isEmpty())
                    ? root
                    : root.getSection(basePath);

            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);

                if (field.isAnnotationPresent(ItemRoot.class)) {
                    Class<?> ft = field.getType();
                    if (!ConfigurationSection.class.isAssignableFrom(ft)) {
                        throw new IllegalArgumentException(
                                "@ItemRoot field " + clazz.getName() + "." + field.getName() + " must be assignable to ConfigurationSection");
                    }
                    field.set(instance, effectiveRoot);
                    continue;
                }

                if (field.isAnnotationPresent(YamlId.class)
                        || field.getName().startsWith("__vinject_yaml")) {
                    String keyPath = getKeyPath(field, basePath);
                    Object value = root.get(keyPath);
                    if (value != null) {
                        Object converted = convertValue(value, field.getGenericType(), field);
                        field.set(instance, converted);
                    }
                    continue;
                }

                String keyPath = getKeyPath(field, basePath);
                if (root == null || !root.contains(keyPath)) {
                    MISSING_KEYS_TRACKER.set(true);
                    continue;
                }

                Object value = root.get(keyPath);
                if (value == null) {
                    continue;
                }

                Object converted = convertValue(value, field.getGenericType(), field);
                field.set(instance, converted);
            }

            if (isTopLevel) {
                return MISSING_KEYS_TRACKER.get();
            }
            return false;
        } finally {
            if (isTopLevel) {
                MISSING_KEYS_TRACKER.remove();
            }
        }
    }

    public String getKeyPath(Field field, String basePath) {
        if (field.isAnnotationPresent(Key.class)) {
            String val = field.getAnnotation(Key.class).value();
            return (basePath == null || basePath.isEmpty()) ? val : basePath + "." + val;
        }
        String key = field.getName();
        return (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key;
    }

    @SuppressWarnings("unchecked")
    public Object convertValue(Object value, Type targetType, Field field) throws Exception {
        if (value == null || "~".equals(value)) return null;

        Class<?> targetClass = getRawClass(targetType);

        YamlSerializerBase<?> ser = YamlSerializerRegistry.getSerializer(targetClass);
        if (ser != null) {
            YamlSerializerBase<Object> s = (YamlSerializerBase<Object>) ser;
            if (value instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) value;
                return s.deserialize(m);
            }
        }

        if (targetClass.isAssignableFrom(value.getClass())
                && !Map.class.isAssignableFrom(targetClass)
                && !List.class.isAssignableFrom(targetClass)
                && !targetClass.isEnum()
                && !targetClass.isAnnotationPresent(YamlItem.class)) {
            if (isPrimitiveOrString(targetClass) || targetClass == Object.class) {
                return value;
            }
        }

        if (targetClass.isEnum()) {
            if (targetClass.isAssignableFrom(value.getClass())) return value;
            String enumName = value.toString();
            try {
                return Enum.valueOf(targetClass.asSubclass(Enum.class), enumName);
            } catch (IllegalArgumentException e) {
                try {
                    return Enum.valueOf(targetClass.asSubclass(Enum.class), enumName.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    throw new RuntimeException("Invalid enum value '" + enumName + "' for enum type " + targetClass.getName() + (field != null ? " (field: " + field.getName() + ")" : ""), e);
                }
            }
        }

        if (targetClass == String.class) return value.toString();
        if (targetClass == int.class || targetClass == Integer.class) return (value instanceof Number n) ? n.intValue() : Integer.parseInt(value.toString());
        if (targetClass == long.class || targetClass == Long.class) return (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString());
        if (targetClass == boolean.class || targetClass == Boolean.class) return (value instanceof Boolean b) ? b : Boolean.parseBoolean(value.toString());
        if (targetClass == double.class || targetClass == Double.class) return (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(value.toString());

        if (targetClass == float.class || targetClass == Float.class) return (value instanceof Number n) ? n.floatValue() : Float.parseFloat(value.toString());
        if (targetClass == short.class || targetClass == Short.class) return (value instanceof Number n) ? n.shortValue() : Short.parseShort(value.toString());
        if (targetClass == byte.class || targetClass == Byte.class) return (value instanceof Number n) ? n.byteValue() : Byte.parseByte(value.toString());
        if (targetClass == char.class || targetClass == Character.class) {
            String text = value.toString();
            if (text.length() != 1) {
                throw new IllegalArgumentException("Expected a single character, got '" + text + "'");
            }
            return text.charAt(0);
        }

        if (targetClass == ConfigurationSection.class && value instanceof ConfigurationSection) {
            return value;
        }

        if (targetClass.isArray()) {
            List<?> sourceItems = asSequence(value);
            Class<?> componentType = targetClass.getComponentType();
            Object resultArray = Array.newInstance(componentType, sourceItems.size());
            for (int i = 0; i < sourceItems.size(); i++) {
                Object converted = convertValue(sourceItems.get(i), componentType, null);
                Array.set(resultArray, i, converted);
            }
            return resultArray;
        }

        if (List.class.isAssignableFrom(targetClass)) {
            List<Object> resultList = new ArrayList<>();
            Type elementType = Object.class;

            if (targetType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0) {
                    elementType = typeArgs[0];
                }
            }

            for (Object item : asSequence(value)) {
                resultList.add(convertValue(item, elementType, null));
            }
            return resultList;
        }

        if (Map.class.isAssignableFrom(targetClass) && (value instanceof Map || value instanceof ConfigurationSection)) {
            Map<String, Object> yamlMap;
            if (value instanceof Map) {
                yamlMap = (Map<String, Object>) value;
            } else {
                yamlMap = new LinkedHashMap<>();
                ConfigurationSection section = (ConfigurationSection) value;
                for (String key : section.getKeys(false)) {
                    yamlMap.put(key, section.get(key));
                }
            }

            Type keyType = String.class;
            Type valueType = Object.class;

            if (targetType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length >= 1) {
                    keyType = typeArgs[0];
                }
                if (typeArgs.length >= 2) {
                    valueType = typeArgs[1];
                }
            }

            Map<Object, Object> resultMap;
            try {
                if (targetClass.isInterface()) {
                    resultMap = new LinkedHashMap<>();
                } else {
                    resultMap = (Map<Object, Object>) targetClass.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                resultMap = new LinkedHashMap<>();
            }

            for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
                Object key = entry.getKey();
                Class<?> keyClass = getRawClass(keyType);
                if (keyClass != String.class && !keyClass.isAssignableFrom(key.getClass())) {
                    key = convertValue(key, keyType, null);
                }

                Object convertedValue = convertValue(entry.getValue(), valueType, null);

                if (convertedValue != null && key != null) {
                    Field idField = findIdFieldForClass(convertedValue.getClass());
                    if (idField != null) {
                        try {
                            idField.setAccessible(true);
                            Object convertedKey = convertValue(key, idField.getGenericType(), idField);
                            idField.set(convertedValue, convertedKey);
                        } catch (Exception ignored) {}
                    }
                }

                resultMap.put(key, convertedValue);
            }

            return resultMap;
        }

        if (value instanceof Map || value instanceof ConfigurationSection) {
            Object nestedObj = factory.newInstance(targetClass);

            if (value instanceof ConfigurationSection section) {
                mapToInstance(section, nestedObj, targetClass, "");
            } else if (value instanceof Map mapVal) {
                Map<String, Object> stringMap = (Map<String, Object>) mapVal;
                mapToInstance(YamlConfig.fromMap(stringMap), nestedObj, targetClass, "");
            }
            return nestedObj;
        }

        if (Map.class.isAssignableFrom(targetClass) || List.class.isAssignableFrom(targetClass)) {
            return null;
        }

        return value;
    }

    private List<?> asSequence(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(Array.get(value, i));
            }
            return result;
        }
        if (value instanceof String string) {
            Object parsed = YamlValueFormatter.deserialize(string);
            if (parsed instanceof List<?> list) {
                return list;
            }
            if (string.trim().isEmpty() || "[]".equals(string.trim())) {
                return List.of();
            }
        }
        return List.of(value);
    }

    public Field findIdFieldForClass(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(YamlId.class)) {
                return f;
            }
        }
        return null;
    }

    private boolean isPrimitiveOrString(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == String.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Double.class ||
                clazz == Float.class ||
                clazz == Boolean.class ||
                clazz == Byte.class ||
                clazz == Short.class ||
                clazz == Character.class;
    }

    private Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) return (Class<?>) type;
        if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?>) return (Class<?>) pt.getRawType();
        }
        return Object.class;
    }
}
