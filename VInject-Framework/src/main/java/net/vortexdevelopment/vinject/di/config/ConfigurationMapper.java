package net.vortexdevelopment.vinject.di.config;

import net.vortexdevelopment.vinject.annotation.yaml.Comment;
import net.vortexdevelopment.vinject.annotation.yaml.Key;
import net.vortexdevelopment.vinject.annotation.yaml.YamlId;
import net.vortexdevelopment.vinject.annotation.yaml.YamlItem;
import net.vortexdevelopment.vinject.config.ConfigurationSection;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerBase;
import net.vortexdevelopment.vinject.config.yaml.YamlConfig;
import net.vortexdevelopment.vinject.di.DependencyContainer;
import net.vortexdevelopment.vinject.config.serializer.YamlSerializerRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles mapping between YAML configuration sections and Java objects.
 * Separated from ConfigurationContainer to reduce complexity.
 */
public class ConfigurationMapper {

    private final DependencyContainer container;


    public ConfigurationMapper(DependencyContainer container) {
        this.container = container;
    }

    public void registerSerializer(YamlSerializerBase<?> serializer) {
        YamlSerializerRegistry.registerSerializer(serializer);
    }

    public void mapToInstance(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);

            String keyPath = getKeyPath(field, basePath);
            Object value = root.get(keyPath);
            if (value == null) continue; // keep default

            // Pass generic type to support complex types (Maps, Lists)
            Object converted = convertValue(value, field.getGenericType(), field);
            field.set(instance, converted);
        }
    }

    public void applyToConfig(ConfigurationSection root, Object instance, Class<?> clazz, String basePath) throws Exception {
        // holistic sync: treat memory as the absolute source of truth
        // This will replace the section at basePath with the current state of instance
        if (clazz.isAnnotationPresent(YamlItem.class) 
                || YamlSerializerRegistry.hasSerializer(clazz)) {
            root.set(basePath, instance);
        } else {
            // Fallback for classes that aren't explicitly annotated but still used in config (rare in VInject)
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                // Skip YamlId and synthetic fields
                if (field.isAnnotationPresent(YamlId.class) || field.getName().startsWith("__vinject_yaml")) continue;
                field.setAccessible(true);

                String keyPath = getKeyPath(field, basePath);
                Object value = field.get(instance);

                // Handle comments
                if (field.isAnnotationPresent(Comment.class)) {
                    Comment comment = field.getAnnotation(Comment.class);
                    String commentText = String.join("\n", comment.value());
                    root.set(keyPath, value, commentText);
                } else {
                    root.set(keyPath, value);
                }
            }
        }
    }

    private String getKeyPath(Field field, String basePath) {
        if (field.isAnnotationPresent(Key.class)) {
            String val = field.getAnnotation(Key.class).value();
            return (basePath == null || basePath.isEmpty()) ? val : basePath + "." + val;
        }
        String key = field.getName();
        return (basePath == null || basePath.isEmpty()) ? key : basePath + "." + key;
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(Object value, Type targetType, Field field) throws Exception {
        if (value == null || "~".equals(value)) return null;

        Class<?> targetClass = getRawClass(targetType);

        // 1. If a custom serializer exists for this target type, use it
        YamlSerializerBase<?> ser = YamlSerializerRegistry.getSerializer(targetClass);
        if (ser != null) {
            YamlSerializerBase<Object> s = (YamlSerializerBase<Object>) ser;
            if (value instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) value;
                return s.deserialize(m);
            }
        }

        // 2. Early return for already-compatible simple types (non-collection, non-recursive, non-enum)
        // We must NOT return early for Maps and Lists because we need to convert their contents
        if (targetClass.isAssignableFrom(value.getClass())
                && !Map.class.isAssignableFrom(targetClass)
                && !List.class.isAssignableFrom(targetClass)
                && !targetClass.isEnum()
                && !targetClass.isAnnotationPresent(YamlItem.class)) {
            // Check if it's a simple type or if we should still process it
            if (isPrimitiveOrString(targetClass) || targetClass == Object.class) {
                return value;
            }
        }

        // Handle enum types - convert string to enum constant using .name()
        if (targetClass.isEnum()) {
            if (targetClass.isAssignableFrom(value.getClass())) return value;
            String enumName = value.toString();
            try {
                // Use reflection to call valueOf method on the enum class
                Method valueOfMethod = targetClass.getMethod("valueOf", String.class);
                return valueOfMethod.invoke(null, enumName);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
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

        // Handle Map types - convert YAML map to target Map type with proper value conversion
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

            // Get generic type parameters from targetType
            Type keyType = String.class; // Default to String for keys
            Type valueType = Object.class; // Default to Object for values

            if (targetType instanceof ParameterizedType pt) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length >= 1) {
                    keyType = typeArgs[0];
                }
                if (typeArgs.length >= 2) {
                    valueType = typeArgs[1];
                }
            }

            // Create a new map instance of the target type
            Map<Object, Object> resultMap;
            try {
                if (targetClass.isInterface()) {
                    resultMap = new java.util.LinkedHashMap<>();
                } else {
                    resultMap = (Map<Object, Object>) targetClass.getDeclaredConstructor().newInstance();
                }
            } catch (Exception e) {
                // Fallback to LinkedHashMap if default constructor not available or is interface
                resultMap = new java.util.LinkedHashMap<>();
            }

            // Convert each entry, converting keys and values to their target types
            for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
                Object key = entry.getKey();
                // Convert key if needed (usually String, but handle other types)
                Class<?> keyClass = getRawClass(keyType);
                if (keyClass != String.class && !keyClass.isAssignableFrom(key.getClass())) {
                    key = convertValue(key, keyType, null);
                }

                // Convert value to target value type
                Object convertedValue = convertValue(entry.getValue(), valueType, null);

                // If the converted value has a @YamlId field, inject the key into it
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

        // For nested objects, assume map or use serializer
        if (value instanceof Map || value instanceof ConfigurationSection) {
            // Use prototype instance for nested objects to avoid singleton sharing
            Object nestedObj = container.newInstance(targetClass, false);

            if (value instanceof ConfigurationSection section) {
                mapToInstance(section, nestedObj, targetClass, "");
            } else if (value instanceof Map mapVal) {
                Map<String, Object> stringMap = (Map<String, Object>) mapVal;
                mapToInstance(YamlConfig.fromMap(stringMap), nestedObj, targetClass, "");
            }
            return nestedObj;
        }

        // If we expected a Map/List but got something else (and it wasn't null/~), return null to avoid Field.set crash
        if (Map.class.isAssignableFrom(targetClass) || List.class.isAssignableFrom(targetClass)) {
            return null;
        }

        // Fallback: try to convert via string
        return value;
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

    public Field findIdFieldForClass(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(YamlId.class)) {
                return f;
            }
        }
        return null;
    }

    private Class<?> getRawClass(Type type) {
        if (type instanceof Class<?>) return (Class<?>) type;
        if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?>) return (Class<?>) pt.getRawType();
        }
        return Object.class;
    }
}
