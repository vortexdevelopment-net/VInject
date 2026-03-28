package net.vortexdevelopment.vinject.config;

import net.vortexdevelopment.vinject.annotation.yaml.ItemRoot;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
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

    public void mapToInstance(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        int itemRootCount = 0;
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.isAnnotationPresent(ItemRoot.class)) itemRootCount++;
        }
        if (itemRootCount > 1) {
            throw new IllegalArgumentException("At most one @ItemRoot field is allowed on " + clazz.getName());
        }

        ConfigurationSection effectiveRoot = (basePath == null || basePath.isEmpty())
                ? root
                : root.getSection(basePath);

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
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

            String keyPath = getKeyPath(field, basePath);
            Object value = root.get(keyPath);
            if (value == null) continue;

            Object converted = convertValue(value, field.getGenericType(), field);
            field.set(instance, converted);
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
                throw new RuntimeException("Invalid enum value '" + enumName + "' for enum type " + targetClass.getName() + (field != null ? " (field: " + field.getName() + ")" : ""), e);
            }
        }

        if (targetClass == String.class) return value.toString();
        if (targetClass == int.class || targetClass == Integer.class) return (value instanceof Number n) ? n.intValue() : Integer.parseInt(value.toString());
        if (targetClass == long.class || targetClass == Long.class) return (value instanceof Number n) ? n.longValue() : Long.parseLong(value.toString());
        if (targetClass == boolean.class || targetClass == Boolean.class) return (value instanceof Boolean b) ? b : Boolean.parseBoolean(value.toString());
        if (targetClass == double.class || targetClass == Double.class) return (value instanceof Number n) ? n.doubleValue() : Double.parseDouble(value.toString());

        if (targetClass == ConfigurationSection.class && value instanceof ConfigurationSection) {
            return value;
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

            if (value instanceof List<?> list) {
                for (Object item : list) {
                    resultList.add(convertValue(item, elementType, null));
                }
            } else {
                resultList.add(convertValue(value, elementType, null));
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
                            idField.set(convertedValue, key.toString());
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
